package org.workflow.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.postgresql.PGConnection
import org.postgresql.PGNotification
import org.workflow.entity.ExecutionStatus
import org.workflow.repository.ExecutionRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.sql.DataSource

/** Payload sent to the frontend over SSE whenever an execution's status changes. */
data class ExecutionEvent(
    val executionId: String,
    val status: String,
    val taskStatuses: Map<String, String> = emptyMap(),
    val terminal: Boolean = false
)

/** Bridges PostgreSQL NOTIFY events (fired by [ExecutionService]) to browser clients via SSE. */
@Service
class ExecutionEventService(
    private val dataSource: DataSource,
    private val objectMapper: ObjectMapper,
    private val executionRepository: ExecutionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** executionId -> subscribed browser clients for that execution. */
    private val emitters = ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>()

    /** Statuses that trigger notification */
    private val activeStatuses = setOf(ExecutionStatus.PENDING, ExecutionStatus.RUNNING)

    @Volatile private var running = true
    private lateinit var listenerThread: Thread

    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5 * 60 * 1000L // an SSE connection is dropped after 5 min of inactivity
        private const val POLL_TIMEOUT_MS = 5_000                  // how long each getNotifications() call blocks at most (ms)
        private const val RECONNECT_DELAY_MS = 5_000L              // wait time before retrying a dropped DB connection
    }

    /* ---------- Postgres --------*/

    @PostConstruct
    fun startListening() {
        listenerThread = Thread.ofVirtual().name("execution-events-listener").start(::listenWithReconnect)
    }

    @PreDestroy
    fun stopListening() {
        running = false
        listenerThread.interrupt()
    }

    private fun listenWithReconnect() {
        while (running) {
            try {
                listenOnConnection()
            } catch (ex: Exception) {
                if (running) {
                    log.error("Listener connection dropped, reconnecting in {}ms: {}", RECONNECT_DELAY_MS, ex.message)
                    Thread.sleep(RECONNECT_DELAY_MS)
                }
            }
        }
    }

    /** Opens a single connection, issues LISTEN, and forwards notifications until it fails or the app shuts down. */
    private fun listenOnConnection() {
        dataSource.connection.use { connection ->
            val pgConnection = connection.unwrap(PGConnection::class.java)
            connection.createStatement().use { it.execute("LISTEN execution_events") }
            log.info("LISTEN execution_events started")

            while (running) {
                val notifications = pgConnection.getNotifications(POLL_TIMEOUT_MS) ?: continue
                notifications.forEach { n -> handleNotification(n) }
            }
        }
    }

    private fun handleNotification(notification: PGNotification) {
        try {
            val event = objectMapper.readValue(notification.parameter, ExecutionEvent::class.java)
            broadcast(UUID.fromString(event.executionId), event)
        } catch (ex: Exception) {
            log.warn("Failed to parse execution_events notification: {}", ex.message)
        }
    }

    /* ---------- Browser ----------*/

    /** Registers a new SSE subscription */
    fun subscribe(executionId: UUID): SseEmitter {
        val emitter = SseEmitter(SUBSCRIPTION_TIMEOUT_MS)

        // computeIfAbsent is a special method for concurrent writes on a shared hashmap
        emitters.computeIfAbsent(executionId) { CopyOnWriteArrayList() }.add(emitter)

        val cleanup = Runnable { removeEmitter(executionId, emitter) }
        emitter.onCompletion(cleanup)
        emitter.onTimeout(cleanup)
        emitter.onError { cleanup.run() }

        sendCatchUpEvent(executionId, emitter, cleanup)
        return emitter
    }

    private fun sendCatchUpEvent(executionId: UUID, emitter: SseEmitter, cleanup: Runnable) {
        val execution = executionRepository.findByIdOrNull(executionId) ?: return
        val isTerminal = execution.status !in activeStatuses
        val taskStatuses = executionRepository.findTaskStatusesByParentId(executionId)
            .map { row -> row[0].toString() to row[1].toString() }
            .toMap()

        val event = ExecutionEvent(executionId.toString(), execution.status, taskStatuses, isTerminal)

        try {
            emitter.send(SseEmitter.event().name("execution").data(objectMapper.writeValueAsString(event)))
            if (isTerminal) {
                emitter.complete()
                emitters.remove(executionId)
            }
        } catch (ex: Exception) {
            log.warn("Failed to send catch-up event for execution {}: {}", executionId, ex.message)
            cleanup.run()
        }
    }

    /** Sends [event] to every client currently subscribed to [executionId]. */
    fun broadcast(executionId: UUID, event: ExecutionEvent) {
        val subscribers = emitters[executionId] ?: return

        // A send fails when the browser tab is gone — drop that emitter instead of retrying it.
        subscribers.removeIf { emitter -> !trySend(emitter, event) }

        // Once an execution reaches a final status, no further events will ever be sent for it.
        if (event.terminal) emitters.remove(executionId)
    }

    private fun trySend(emitter: SseEmitter, event: ExecutionEvent): Boolean =
        try {
            emitter.send(SseEmitter.event().name("execution").data(objectMapper.writeValueAsString(event)))
            if (event.terminal) emitter.complete()
            true
        } catch (ex: Exception) {
            false
        }

    private fun removeEmitter(executionId: UUID, emitter: SseEmitter) {
        emitters.computeIfPresent(executionId) { _, list ->
            list.remove(emitter)
            list.ifEmpty { null }
        }
    }
}
