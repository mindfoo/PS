package org.workflow.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import org.workflow.entity.Execution
import org.workflow.entity.ExecutionStatus
import org.workflow.entity.ExecutionTriggerType
import org.workflow.entity.ExecutionType
import org.workflow.entity.Task
import org.workflow.entity.User
import org.workflow.entity.Workflow
import org.workflow.repository.ExecutionLogRepository
import org.workflow.repository.TaskRepository
import org.workflow.repository.WorkflowRepository
import org.workflow.repository.WorkflowTaskOrderRepository
import org.workflow.service.utils.ExecutionError
import org.workflow.utils.Either
import org.workflow.utils.failure
import org.workflow.utils.success
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/** Creates and runs workflow/task executions, including manual and cron-triggered flows. */
@Service
class ExecutionService(
    private val executionLogRepository: ExecutionLogRepository,
    private val workflowRepository: WorkflowRepository,
    private val workflowTaskOrderRepository: WorkflowTaskOrderRepository,
    private val taskRepository: TaskRepository,
    private val restTemplate: RestTemplate,
    private val helpers: ServiceHelpers,
    @Qualifier("executionExecutor") private val executionExecutor: Executor,
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MIN_TASK_RUNNING_MS = 2_000L

        /** Splits a raw, space-separated argument string (e.g. "--flag value") into a list. */
        private val WHITESPACE = Regex("\\s+")
    }

    // ---------- Public API ----------

    /** Cancels a PENDING/RUNNING execution. Non-admins can only cancel their own. */
    @Transactional
    fun cancelExecution(executionId: UUID, authenticationName: String): Either<ExecutionError, Unit> {
        val user = helpers.findUser(authenticationName)
            ?: return failure(ExecutionError.UserNotFound)

        val execution = executionLogRepository.findByIdOrNull(executionId)
            ?: return failure(ExecutionError.NotCancelable)

        if (!isAdmin(user) && execution.triggeredBy.id != user.id)
            return failure(ExecutionError.NotCancelable)

        val updated = executionLogRepository.requestCancellation(executionId)
        if (updated == 0) return failure(ExecutionError.NotCancelable)

        cancelPendingChildren(executionId)
        return success(Unit)
    }

    /** Creates a PENDING workflow execution and runs it asynchronously; returns its id right away. */
    @Transactional
    fun triggerManualWorkflow(workflowId: UUID, authenticationName: String): Either<ExecutionError, UUID> {
        val user = helpers.findUser(authenticationName)
            ?: return failure(ExecutionError.UserNotFound)

        val userId = user.id ?: return failure(ExecutionError.UserNotFound)
        val workflow = (if (isAdmin(user)) workflowRepository.findByIdOrNull(workflowId)
                        else workflowRepository.findByIdAndOwnerId(workflowId, userId))
            ?: return failure(ExecutionError.WorkflowNotFound)

        val execution = executionLogRepository.save(
            Execution(
                triggeredType = ExecutionTriggerType.MANUAL,
                type = ExecutionType.WORKFLOW,
                status = ExecutionStatus.PENDING,
                startedAt = LocalDateTime.now(),
                triggeredBy = user,
                workflow = workflow
            )
        )

        val executionId = checkNotNull(execution.id) { "Execution id null after save — data integrity violation" }

        // Runs on a virtual thread so this call returns immediately; the client tracks progress via SSE.
        CompletableFuture.runAsync({ runExecution(executionId) }, executionExecutor)
        return success(executionId)
    }

    /** Creates a PENDING workflow execution for a scheduled (CRON) run. */
    @Transactional
    fun createCronExecution(workflow: Workflow, triggeredBy: User): UUID {
        val execution = executionLogRepository.save(
            Execution(
                triggeredType = ExecutionTriggerType.CRON,
                type = ExecutionType.WORKFLOW,
                status = ExecutionStatus.PENDING,
                startedAt = LocalDateTime.now(),
                triggeredBy = triggeredBy,
                workflow = workflow
            )
        )
        return checkNotNull(execution.id) { "Execution id null after save — data integrity violation" }
    }

    // ---------- Workflow execution engine ----------

    /** Runs a workflow's stages in order; tasks in the same stage run in parallel. Called on a virtual thread. */
    fun runExecution(executionId: UUID) {
        val execution = executionLogRepository.findByIdOrNull(executionId) ?: run {
            log.error("Execution '{}' not found — cannot run", executionId)
            return
        }

        val workflow = execution.workflow ?: run {
            execution.status = ExecutionStatus.ERROR
            execution.finishedAt = LocalDateTime.now()
            execution.output = mapOf("error" to "Missing workflow for execution")
            executionLogRepository.save(execution)
            return
        }

        execution.status = ExecutionStatus.RUNNING
        execution.startedAt = LocalDateTime.now()
        executionLogRepository.save(execution)
        notifyStatusChange(executionId, ExecutionStatus.RUNNING)

        val logLines = mutableListOf("[${LocalDateTime.now()}] Execution $executionId started for workflow '${workflow.name}'")
        var wasCanceled = false

        try {
            val wfId = workflow.id
                ?: throw IllegalStateException("Workflow id is null for execution $executionId — data integrity violation")

            val orderRows = workflowTaskOrderRepository.findAllByWorkflowIdOrderByTaskOrderAsc(wfId).ifEmpty { null }

            val stages: Map<Int, List<Task>> = orderRows
                ?.groupBy({ it.taskOrder }, { it.task })
                ?: taskRepository.findAllByWorkflowId(wfId).mapIndexed { idx, task -> idx to listOf(task) }.toMap()

            val retryPolicies: Map<UUID, Int> = orderRows
                ?.mapNotNull { row -> row.task.id?.let { id -> id to row.retryPolicy } }
                ?.toMap() ?: emptyMap()

            // One PENDING child execution per task, created up front so the UI can show them immediately.
            val taskExecIds: Map<UUID, UUID> = stages.values.flatten()
                .mapNotNull { task -> task.id?.let { id -> id to createPendingTaskExecution(execution, task) } }
                .toMap()

            // Per-task status, sent along with every NOTIFY so subscribers see live progress.
            val taskStatusMap = mutableMapOf<String, String>()
            stages.values.flatten().forEach { task -> task.id?.let { taskStatusMap[it.toString()] = ExecutionStatus.PENDING } }

            var totalExecuted = 0
            val taskOutputs = mutableListOf<Map<String, Any>>()

            for ((stage, tasksInStage) in stages.entries.sortedBy { it.key }) {
                if (executionLogRepository.isCancelRequested(executionId)) {
                    wasCanceled = true
                    break
                }

                logLines += "[${LocalDateTime.now()}] Stage $stage — ${tasksInStage.size} task(s)"

                val results = tasksInStage.mapNotNull { task ->
                    val taskId = task.id ?: return@mapNotNull null
                    val childId = taskExecIds[taskId] ?: return@mapNotNull null
                    CompletableFuture.supplyAsync(
                        { task to runTaskWithTracking(task, childId, retryPolicies[taskId] ?: 0) },
                        executionExecutor
                    )
                }.map { it.join() }

                results.forEach { (task, output) ->
                    taskOutputs += output
                    logLines += formatTaskLog(task, output)
                    task.id?.let { taskStatusMap[it.toString()] = (output["status"] as? String) ?: ExecutionStatus.ERROR }
                }
                totalExecuted += results.size
                notifyStatusChange(executionId, ExecutionStatus.RUNNING, taskStatusMap)

                if (executionLogRepository.isCancelRequested(executionId)) {
                    wasCanceled = true
                    break
                }
            }

            if (wasCanceled) cancelPendingChildren(executionId)

            val anyTaskFailed = taskOutputs.any { (it["status"] as? String) == ExecutionStatus.ERROR }
            execution.status = when {
                wasCanceled   -> ExecutionStatus.CANCELED
                anyTaskFailed -> ExecutionStatus.ERROR
                else          -> ExecutionStatus.SUCCESS
            }
            execution.finishedAt = LocalDateTime.now()
            execution.output = if (wasCanceled) {
                mapOf("info" to "Canceled by user")
            } else {
                mapOf("workflowId" to wfId.toString(), "tasksExecuted" to totalExecuted, "taskOutputs" to taskOutputs)
            }

        } catch (ex: Exception) {
            execution.status = ExecutionStatus.ERROR
            execution.retryCount += 1
            execution.finishedAt = LocalDateTime.now()
            execution.output = mapOf(
                "error" to (ex.message ?: "Unknown execution error"),
                "retryCount" to execution.retryCount
            )
            logLines += "[${LocalDateTime.now()}] ERROR: ${ex.message}"
            log.error("Execution {} failed: {}", executionId, ex.message, ex)
        }

        executionLogRepository.save(execution)
        logLines += "[${LocalDateTime.now()}] Execution finished — status: ${execution.status}"
        log.info("Execution {} finished — status: {}", executionId, execution.status)

        writeLogFile("execution-$executionId", logLines)
        notifyStatusChange(executionId, execution.status, emptyMap(), terminal = true)
    }

    /** Creates a PENDING task execution and runs it asynchronously; returns its id right away. */
    @Transactional
    fun triggerManualTask(taskId: UUID, authenticationName: String): Either<ExecutionError, UUID> {
        val user = helpers.findUser(authenticationName)
            ?: return failure(ExecutionError.UserNotFound)

        val userId = user.id ?: return failure(ExecutionError.UserNotFound)
        val task = (if (isAdmin(user)) taskRepository.findByIdOrNull(taskId)
                    else taskRepository.findByIdAndOwnerId(taskId, userId))
            ?: return failure(ExecutionError.TaskNotFound)

        val execution = executionLogRepository.save(
            Execution(
                triggeredType = ExecutionTriggerType.MANUAL,
                type = ExecutionType.TASK,
                status = ExecutionStatus.PENDING,
                startedAt = LocalDateTime.now(),
                triggeredBy = user,
                task = task,
                workflow = null,
                taskName = task.name
            )
        )

        val executionId = checkNotNull(execution.id) { "Execution id null after save — data integrity violation" }
        CompletableFuture.runAsync({ runManualTask(executionId, task) }, executionExecutor)
        return success(executionId)
    }

    // ---------- Single task execution ----------

    /** Runs a single manually-triggered task and records its outcome. Runs on a virtual thread. */
    private fun runManualTask(executionId: UUID, task: Task) {
        val execRecord = executionLogRepository.findByIdOrNull(executionId) ?: return

        if (execRecord.cancelRequested) {
            execRecord.status = ExecutionStatus.CANCELED
            execRecord.finishedAt = LocalDateTime.now()
            execRecord.output = mapOf("info" to "Canceled by user")
            executionLogRepository.save(execRecord)
            notifyStatusChange(executionId, ExecutionStatus.CANCELED, terminal = true)
            return
        }

        execRecord.status = ExecutionStatus.RUNNING
        execRecord.startedAt = LocalDateTime.now()
        executionLogRepository.save(execRecord)
        notifyStatusChange(executionId, ExecutionStatus.RUNNING)

        val output = runTaskSafely(task)
        execRecord.status = if (output["status"] == ExecutionStatus.SUCCESS) ExecutionStatus.SUCCESS else ExecutionStatus.ERROR
        execRecord.finishedAt = LocalDateTime.now()
        execRecord.output = output
        executionLogRepository.save(execRecord)
        notifyStatusChange(executionId, execRecord.status, terminal = true)

        writeLogFile("task-${task.id}-$executionId", listOf(
            "[${LocalDateTime.now()}] Task '${task.name}' (${task.id}) — manual run",
            "[${LocalDateTime.now()}] Status: ${execRecord.status}",
            output["stdout"]?.toString() ?: output["body"]?.toString() ?: ""
        ))
    }

    private fun createPendingTaskExecution(parent: Execution, task: Task): UUID {
        val child = executionLogRepository.save(
            Execution(
                triggeredType = parent.triggeredType,
                type = ExecutionType.TASK,
                status = ExecutionStatus.PENDING,
                startedAt = LocalDateTime.now(),
                triggeredBy = parent.triggeredBy,
                task = task,
                workflow = parent.workflow,
                parentExecutionId = parent.id,
                taskName = task.name
            )
        )
        return checkNotNull(child.id) { "Child execution id null after save — data integrity violation" }
    }

    /** Marks every still-PENDING child of [executionId] as CANCELED — running children are left alone. */
    private fun cancelPendingChildren(executionId: UUID) {
        executionLogRepository.findAllByParentExecutionIdOrderByStartedAtAsc(executionId)
            .filter { it.status == ExecutionStatus.PENDING }
            .forEach { child ->
                child.status = ExecutionStatus.CANCELED
                child.finishedAt = LocalDateTime.now()
                executionLogRepository.save(child)
            }
    }

    /** Runs [task] against its child execution record, retrying up to [retryPolicy] times on failure. */
    private fun runTaskWithTracking(task: Task, childExecutionId: UUID, retryPolicy: Int = 0): Map<String, Any> {
        val child = executionLogRepository.findByIdOrNull(childExecutionId)
            ?: return mapOf(
                "taskId"   to task.id.toString(),
                "taskName" to task.name,
                "status"   to ExecutionStatus.ERROR,
                "error"    to "Child execution record not found"
            )
        child.status = ExecutionStatus.RUNNING
        child.startedAt = LocalDateTime.now()
        executionLogRepository.save(child)

        var attempt = 0
        var output: Map<String, Any>
        do {
            if (attempt > 0) {
                child.retryCount = attempt
                executionLogRepository.save(child)
                Thread.sleep(2_000L)
            }
            output = runTaskSafely(task)
            attempt++
        } while (output["status"] != ExecutionStatus.SUCCESS && attempt <= retryPolicy)

        child.status = if (output["status"] == ExecutionStatus.SUCCESS) ExecutionStatus.SUCCESS else ExecutionStatus.ERROR
        child.retryCount = attempt - 1
        child.finishedAt = LocalDateTime.now()
        child.output = output
        executionLogRepository.save(child)
        return output
    }

    /** Runs [task], turning any exception into an ERROR result; pads short runs to a minimum visible duration. */
    private fun runTaskSafely(task: Task): Map<String, Any> {
        val startedMs = System.currentTimeMillis()
        val output = try {
            runTask(task)
        } catch (ex: Exception) {
            mapOf(
                "taskId"   to task.id.toString(),
                "taskName" to task.name,
                "status"   to ExecutionStatus.ERROR,
                "error"    to (ex.message ?: "Unexpected error")
            )
        }
        val elapsed = System.currentTimeMillis() - startedMs
        if (elapsed < MIN_TASK_RUNNING_MS) Thread.sleep(MIN_TASK_RUNNING_MS - elapsed)
        return output
    }

    private fun formatTaskLog(task: Task, output: Map<String, Any>): String =
        "[${LocalDateTime.now()}] Task '${task.name}' (${task.id}) — status: ${output["status"]} | " +
        (output["stdout"]?.toString()?.take(200) ?: output["body"]?.toString()?.take(200) ?: "")

    private fun writeLogFile(name: String, lines: List<String>) {
        try {
            val dir = File("logs").apply { mkdirs() }
            File(dir, "$name.log").writeText(lines.joinToString("\n"))
        } catch (ex: Exception) {
            log.warn("Could not write log file for {}: {}", name, ex.message)
        }
    }

    // ---------- Task runners (SCRIPT / HTTP) ----------

    private fun runTask(task: Task): Map<String, Any> {
        return when (task.type.uppercase()) {
            "SCRIPT" -> runScriptTask(task)
            "HTTP"   -> runHttpTask(task)
            else     -> mapOf("taskId" to task.id.toString(), "type" to task.type, "status" to "SKIPPED", "reason" to "Unsupported task type")
        }
    }

    /** Runs a SCRIPT task via [ProcessBuilder]. Only allowlisted commands are permitted. */
    private fun runScriptTask(task: Task): Map<String, Any> {
        val config = task.config
        val command   = config["command"]  as? String
            ?: return mapOf("taskId" to task.id.toString(), "taskName" to task.name, "status" to ExecutionStatus.ERROR, "error" to "missing 'command' in config")
        val fileName  = config["fileName"] as? String ?: ""
        val directory = config["directory"] as? String
        val rawArgs   = config["args"]

        val allowedCommands = setOf("node", "python3", "bash", "sh", "python")
        if (command !in allowedCommands || command.contains('/') || command.contains('\\')) {
            log.warn("Task '{}' blocked — command '{}' is not on the allowlist", task.id, command)
            return mapOf(
                "taskId"   to task.id.toString(),
                "taskName" to task.name,
                "status"   to ExecutionStatus.ERROR,
                "error"    to "Command '$command' is not permitted. Allowed: $allowedCommands"
            )
        }

        // config["args"] may come in as a JSON array or as a plain "--flag value" string — support both.
        @Suppress("UNCHECKED_CAST")
        val argList: List<String> = when (rawArgs) {
            is List<*> -> rawArgs.filterIsInstance<String>()
            is String  -> rawArgs.trim().split(WHITESPACE).filter { it.isNotEmpty() }
            else       -> emptyList()
        }

        val cmd = buildList {
            add(command)
            if (fileName.isNotBlank()) add(fileName)
            addAll(argList)
        }

        val pb = ProcessBuilder(cmd)
        if (directory != null) pb.directory(File(directory))
        pb.redirectErrorStream(true) // merge stderr into stdout so we capture error output too

        val process = pb.start()
        val (stdout, exitCode) = try {
            val output = process.inputStream.bufferedReader().use { it.readText() }
            output to process.waitFor()
        } finally {
            if (process.isAlive) process.destroyForcibly() // don't leak the OS process if we get interrupted
        }

        log.info("Task '{}' script '{}' exited {} — output: {}", task.id, cmd.joinToString(" "), exitCode, stdout.take(200))

        return mapOf(
            "taskId"   to task.id.toString(),
            "taskName" to task.name,
            "type"     to "SCRIPT",
            "command"  to cmd.joinToString(" "),
            "exitCode" to exitCode,
            "stdout"   to stdout,
            "status"   to if (exitCode == 0) ExecutionStatus.SUCCESS else ExecutionStatus.ERROR
        )
    }

    /** Runs an HTTP task via [RestTemplate]. */
    private fun runHttpTask(task: Task): Map<String, Any> {
        val config = task.config
        val rawUrl = config["url"] as? String
            ?: return mapOf("taskId" to task.id.toString(), "taskName" to task.name, "status" to ExecutionStatus.ERROR, "error" to "missing 'url' in config")
        val method = (config["method"] as? String ?: "GET").uppercase()

        val headers = HttpHeaders()
        @Suppress("UNCHECKED_CAST")
        val headersMap = config["headers"] as? Map<String, String> ?: emptyMap()
        headersMap.forEach { (k, v) -> headers.set(k, v) }

        val body = config["body"] as? String
        val entity = HttpEntity(body, headers)

        val response = restTemplate.exchange(rawUrl, HttpMethod.valueOf(method), entity, String::class.java)

        log.info("Task '{}' HTTP {} {} — status {}", task.id, method, rawUrl, response.statusCode.value())

        return mapOf(
            "taskId"     to task.id.toString(),
            "taskName"   to task.name,
            "type"       to "HTTP",
            "url"        to rawUrl,
            "method"     to method,
            "statusCode" to response.statusCode.value(),
            "body"       to (response.body ?: ""),
            "status"     to if (response.statusCode.is2xxSuccessful) ExecutionStatus.SUCCESS else ExecutionStatus.ERROR
        )
    }

    // ---------- Helpers ----------

    private fun isAdmin(user: User) = helpers.isAdmin(user)

    /** Publishes a pg_notify so [ExecutionEventService] can push the update to subscribed SSE clients. */
    private fun notifyStatusChange(
        executionId: UUID,
        status: String,
        taskStatuses: Map<String, String> = emptyMap(),
        terminal: Boolean = false
    ) {
        try {
            val payload = objectMapper.writeValueAsString(
                mapOf(
                    "executionId"  to executionId.toString(),
                    "status"       to status,
                    "taskStatuses" to taskStatuses,
                    "terminal"     to terminal
                )
            )
            jdbcTemplate.execute<Unit>("SELECT pg_notify('execution_events', ?)") { ps ->
                ps.setString(1, payload)
                ps.execute()
            }
        } catch (e: Exception) {
            log.warn("pg_notify failed for execution {}: {}", executionId, e.message)
        }
    }
}
