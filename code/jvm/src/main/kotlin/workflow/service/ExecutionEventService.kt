package org.workflow.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.postgresql.PGConnection
import org.workflow.entity.ExecutionStatus
import org.workflow.repository.ExecutionLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import javax.sql.DataSource

data class ExecutionEvent(
    val executionId: String,
    val status: String,
    val taskStatuses: Map<String, String> = emptyMap(),
    val terminal: Boolean = false
)

@Service
class ExecutionEventService(
    private val dataSource: DataSource,
    private val objectMapper: ObjectMapper,
    private val executionLogRepository: ExecutionLogRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val emitters = ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>()
    @Volatile private var running = true
    private val listenerExecutor = Executors.newVirtualThreadPerTaskExecutor()

    @PostConstruct
    fun startListening() {
        listenerExecutor.submit {
            // CRITICAL FIX: Outer loop ensures the listener restarts if the connection pool kills the connection
            while (running) {
                try {
                    dataSource.connection.use { conn ->
                        val pgConn = conn.unwrap(PGConnection::class.java)
                        conn.createStatement().use { stmt -> stmt.execute("LISTEN execution_events") }
                        log.info("ExecutionEventService: LISTEN execution_events started")

                        while (running) {
                            // Increased wait to 5000ms to prevent heavy CPU polling overhead
                            val notifications = pgConn.getNotifications(5000)
                            notifications?.forEach { n ->
                                try {
                                    val event = objectMapper.readValue(n.parameter, ExecutionEvent::class.java)
                                    broadcast(UUID.fromString(event.executionId), event)
                                } catch (e: Exception) {
                                    log.warn("Failed to parse execution_events notification: {}", e.message)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (running) {
                        log.error("Database listener connection dropped (likely pool timeout). Reconnecting in 5s...", e.message)
                        Thread.sleep(5000)
                    }
                }
            }
        }
    }

    @PreDestroy
    fun stopListening() {
        running = false
        listenerExecutor.shutdown()
    }

    fun subscribe(executionId: UUID): SseEmitter {
        val emitter = SseEmitter(300_000L) // 5 min timeout
        emitters.getOrPut(executionId) { CopyOnWriteArrayList() }.add(emitter)
        val cleanup = Runnable { removeEmitter(executionId, emitter) }
        emitter.onCompletion(cleanup)
        emitter.onTimeout(cleanup)
        emitter.onError { cleanup.run() }

        executionLogRepository.findById(executionId).orElse(null)?.let { execution ->
            val isTerminal = execution.status !in setOf(ExecutionStatus.PENDING, ExecutionStatus.RUNNING)
            val taskStatuses = executionLogRepository
                .findTaskStatusesByParentId(executionId)
                .associate { row -> row[0].toString() to row[1].toString() }
            try {
                val initial = ExecutionEvent(
                    executionId = executionId.toString(),
                    status = execution.status,
                    taskStatuses = taskStatuses,
                    terminal = isTerminal
                )
                emitter.send(SseEmitter.event().name("execution").data(objectMapper.writeValueAsString(initial)))
                if (isTerminal) {
                    emitter.complete()
                    emitters.remove(executionId)
                }
            } catch (e: Exception) {
                log.warn("Failed to send catch-up event for execution {}: {}", executionId, e.message)
                cleanup.run()
            }
        }

        return emitter
    }

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