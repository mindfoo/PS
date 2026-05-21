package org.workflow.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.postgresql.PGConnection
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import javax.sql.DataSource

/** Payload sent to the frontend over SSE when an execution status changes. */
data class ExecutionEvent(
    val executionId: String,
    val status: String,
    val taskStatuses: Map<String, String> = emptyMap(),
    val terminal: Boolean = false
)

/**
 * Manages SSE client subscriptions and receives PostgreSQL LISTEN/NOTIFY events
 * on the `execution_events` channel. One LISTEN connection is shared across all
 * clients on this application instance.
 */
@Service
class ExecutionEventService(
    private val dataSource: DataSource,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** executionId → list of active SSE emitters for that execution */
    private val emitters = ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>()

    @Volatile private var running = true
    private val listenerExecutor = Executors.newVirtualThreadPerTaskExecutor()

    @PostConstruct
    fun startListening() {
        listenerExecutor.submit {
            try {
                val conn = dataSource.connection
                val pgConn = conn.unwrap(PGConnection::class.java)
                conn.createStatement().use { stmt -> stmt.execute("LISTEN execution_events") }
                log.info("ExecutionEventService: LISTEN execution_events started")

                while (running) {
                    val notifications = pgConn.getNotifications(50) // 50 ms wait
                    notifications?.forEach { n ->
                        try {
                            val event = objectMapper.readValue(n.parameter, ExecutionEvent::class.java)
                            broadcast(UUID.fromString(event.executionId), event)
                        } catch (e: Exception) {
                            log.warn("Failed to parse execution_events notification: {}", e.message)
                        }
                    }
                }
                conn.close()
            } catch (e: Exception) {
                log.error("ExecutionEventService listener failed: {}", e.message, e)
            }
        }
    }

    @PreDestroy
    fun stopListening() {
        running = false
        listenerExecutor.shutdown()
    }

    /** Registers a new SSE emitter for the given execution. */
    fun subscribe(executionId: UUID): SseEmitter {
        val emitter = SseEmitter(300_000L) // 5 min timeout
        emitters.getOrPut(executionId) { CopyOnWriteArrayList() }.add(emitter)
        val cleanup = Runnable { removeEmitter(executionId, emitter) }
        emitter.onCompletion(cleanup)
        emitter.onTimeout(cleanup)
        emitter.onError { cleanup.run() }
        return emitter
    }

    /** Broadcasts an event to all SSE emitters subscribed to this execution. */
    fun broadcast(executionId: UUID, event: ExecutionEvent) {
        val list = emitters[executionId] ?: return
        val dead = mutableListOf<SseEmitter>()
        list.forEach { emitter ->
            try {
                emitter.send(SseEmitter.event().name("execution").data(objectMapper.writeValueAsString(event)))
                if (event.terminal) emitter.complete()
            } catch (e: Exception) {
                dead.add(emitter)
            }
        }
        dead.forEach { removeEmitter(executionId, it) }
        if (event.terminal) emitters.remove(executionId)
    }

    private fun removeEmitter(executionId: UUID, emitter: SseEmitter) {
        emitters[executionId]?.remove(emitter)
    }
}
