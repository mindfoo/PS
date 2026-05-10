package org.workflow.service

import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import org.workflow.entity.Execution
import org.workflow.entity.Task
import org.workflow.entity.User
import org.workflow.entity.Workflow
import org.workflow.repository.ExecutionLogRepository
import org.workflow.repository.TaskRepository
import org.workflow.repository.UserRepository
import org.workflow.repository.WorkflowRepository
import org.workflow.repository.WorkflowTaskOrderRepository
import org.workflow.service.utils.ExecutionError
import org.workflow.utils.Either
import org.workflow.utils.failure
import org.workflow.utils.success
import java.io.File
import java.time.LocalDateTime
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Service
/** Creates and runs workflow/task executions, including manual and cron-triggered flows. */
class ExecutionService(
    private val executionLogRepository: ExecutionLogRepository,
    private val workflowRepository: WorkflowRepository,
    private val workflowTaskOrderRepository: WorkflowTaskOrderRepository,
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()
    /** Execution IDs that have been requested to cancel. Checked between stages and retry loops. */
    private val cancelRequests: MutableSet<UUID> = Collections.newSetFromMap(ConcurrentHashMap())

    companion object {
        private const val MIN_TASK_RUNNING_MS = 2_000L
    }

    // ── Cancellation ──────────────────────────────────────────────────────────

    @Transactional
    fun cancelExecution(executionId: UUID, authenticationName: String): Boolean {
        val user = userRepository.findByUsername(authenticationName)
            ?: return false

        val execution = executionLogRepository.findById(executionId).orElse(null)
            ?: return false

        // Non-admins may only cancel their own executions
        if (!isAdmin(user) && execution.triggeredBy.id != user.id) return false

        if (execution.status !in listOf("PENDING", "RUNNING")) return false

        cancelRequests.add(executionId)
        // Also cancel all pending child executions immediately
        executionLogRepository.findAllByParentExecutionIdOrderByStartedAtAsc(executionId)
            .filter { it.status in listOf("PENDING", "RUNNING") }
            .forEach { child ->
                child.status = "CANCELED"
                child.finishedAt = LocalDateTime.now()
                executionLogRepository.save(child)
            }
        execution.status = "CANCELED"
        execution.finishedAt = LocalDateTime.now()
        execution.output = mapOf("info" to "Canceled by user")
        executionLogRepository.save(execution)
        return true
    }

    // ── Workflow execution ────────────────────────────────────────────────────

    /** Saves a top-level PENDING execution and dispatches async processing. Returns immediately. */
    fun triggerManualWorkflow(workflowId: UUID, authenticationName: String): UUID {
        val user = findUser(authenticationName)
        val workflow = findAccessibleWorkflow(workflowId, user)

        val execution = executionLogRepository.save(
            Execution(
                triggeredType = "MANUAL",
                type = "WORKFLOW",
                status = "PENDING",
                startedAt = LocalDateTime.now(),
                triggeredBy = user,
                workflow = workflow
            )
        )

        CompletableFuture.runAsync { runExecution(execution.id!!) }
        return execution.id!!
    }

    @Transactional
    fun createCronExecution(workflow: Workflow, triggeredBy: User): UUID {
        val execution = executionLogRepository.save(
            Execution(
                triggeredType = "CRON",
                type = "WORKFLOW",
                status = "PENDING",
                startedAt = LocalDateTime.now(),
                triggeredBy = triggeredBy,
                workflow = workflow
            )
        )
        return execution.id!!
    }

    // ── Core workflow runner (called async) ───────────────────────────────────

    fun runExecution(executionId: UUID) {
        val execution = executionLogRepository.findById(executionId).orElseThrow {
            NoSuchElementException("Execution '$executionId' not found")
        }

        if (execution.workflow == null) {
            execution.status = "ERROR"
            execution.finishedAt = LocalDateTime.now()
            execution.output = mapOf("error" to "Missing workflow for execution")
            executionLogRepository.save(execution)
            return
        }

        val workflow = execution.workflow!!
        execution.status = "RUNNING"
        execution.startedAt = LocalDateTime.now()
        executionLogRepository.save(execution)

        val logLines = mutableListOf<String>()
        logLines += "[${LocalDateTime.now()}] Execution $executionId started for workflow '${workflow.name}'"

        try {
            val orderRows = workflowTaskOrderRepository.findAllByWorkflowIdOrderByTaskOrderAsc(workflow.id!!)
                .ifEmpty { null }

            val stages: Map<Int, List<Task>> = if (orderRows != null) {
                orderRows.groupBy({ it.taskOrder }, { it.task })
            } else {
                taskRepository.findAllByWorkflowId(workflow.id!!)
                    .mapIndexed { idx, task -> idx to listOf(task) }
                    .toMap()
            }

            val retryPolicies: Map<UUID, Int> = orderRows?.associate { it.task.id!! to it.retryPolicy } ?: emptyMap()

            // Pre-create PENDING child execution records for every task
            val taskExecIds: Map<UUID, UUID> = stages.values.flatten()
                .mapNotNull { task -> task.id?.let { id -> id to createPendingTaskExecution(execution, task) } }
                .toMap()

            var totalExecuted = 0
            val taskOutputs = mutableListOf<Map<String, Any>>()

            stages.entries.sortedBy { it.key }.forEach { (stage, tasksInStage) ->
                // Check for cancellation before starting each stage
                if (cancelRequests.remove(executionId)) {
                    // Mark remaining PENDING children as CANCELED
                    executionLogRepository.findAllByParentExecutionIdOrderByStartedAtAsc(executionId)
                        .filter { it.status == "PENDING" }
                        .forEach { c -> c.status = "CANCELED"; c.finishedAt = LocalDateTime.now(); executionLogRepository.save(c) }
                    throw CancellationException("Execution canceled")
                }
                logLines += "[${LocalDateTime.now()}] Stage $stage — ${tasksInStage.size} task(s)"
                log.info("Execution {} — stage {} — {} task(s)", executionId, stage, tasksInStage.size)

                if (tasksInStage.size == 1) {
                    val task = tasksInStage.first()
                    val childId = taskExecIds[task.id!!]!!
                    val output = runTaskWithTracking(task, childId, retryPolicies[task.id!!] ?: 0)
                    taskOutputs += output
                    logLines += formatTaskLog(task, output)
                    totalExecuted++
                } else {
                    val futures = tasksInStage.map { task ->
                        val childId = taskExecIds[task.id!!]!!
                        CompletableFuture.supplyAsync { task to runTaskWithTracking(task, childId, retryPolicies[task.id!!] ?: 0) }
                    }
                    CompletableFuture.allOf(*futures.toTypedArray()).join()
                    futures.forEach { f ->
                        val (task, output) = f.get()
                        taskOutputs += output
                        logLines += formatTaskLog(task, output)
                    }
                    totalExecuted += tasksInStage.size
                }
            }

            execution.status = "SUCCESS"
            execution.finishedAt = LocalDateTime.now()
            execution.output = mapOf(
                "workflowId" to workflow.id.toString(),
                "tasksExecuted" to totalExecuted,
                "taskOutputs" to taskOutputs
            )
        } catch (ex: CancellationException) {
            if (execution.status != "CANCELED") {
                execution.status = "CANCELED"
                execution.finishedAt = LocalDateTime.now()
                execution.output = mapOf("info" to "Canceled by user")
            }
            logLines += "[${LocalDateTime.now()}] CANCELED"
            log.info("Execution {} was canceled", executionId)
        } catch (ex: Exception) {
            execution.status = "ERROR"
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
        cancelRequests.remove(executionId) // cleanup in case of early completion
        logLines += "[${LocalDateTime.now()}] Execution finished — status: ${execution.status}"
        writeLogFile("execution-$executionId", logLines)
    }

    // ── Single-task execution ─────────────────────────────────────────────────

    fun triggerManualTask(taskId: UUID, authenticationName: String): Either<ExecutionError, UUID> {
        val user = userRepository.findByUsername(authenticationName)
            ?: return failure(ExecutionError.UserNotFound)

        val task = if (isAdmin(user)) {
            taskRepository.findById(taskId).orElse(null)
        } else {
            taskRepository.findByIdAndOwnerId(taskId, user.id!!)
        } ?: return failure(ExecutionError.TaskNotFound)

        val execution = executionLogRepository.save(
            Execution(
                triggeredType = "MANUAL",
                type = "TASK",
                status = "PENDING",
                startedAt = LocalDateTime.now(),
                triggeredBy = user,
                task = task,
                workflow = null,
                taskName = task.name
            )
        )

        CompletableFuture.runAsync {
            val execRecord = executionLogRepository.findById(execution.id!!).get()
            // Respect cancellation requested before async block started
            if (execRecord.status == "CANCELED") return@runAsync
            execRecord.status = "RUNNING"
            execRecord.startedAt = LocalDateTime.now()
            executionLogRepository.save(execRecord)

            val started = System.currentTimeMillis()
            val output: Map<String, Any>
            try {
                output = runTask(task)
            } catch (ex: Exception) {
                enforceMinRunTime(started)
                execRecord.status = "ERROR"
                execRecord.finishedAt = LocalDateTime.now()
                execRecord.output = mapOf("error" to (ex.message ?: "Unexpected error"))
                executionLogRepository.save(execRecord)
                return@runAsync
            }
            enforceMinRunTime(started)
            val success = (output["exitCode"] as? Int ?: output["statusCode"] as? Int ?: 0) == 0
            execRecord.status = if (success) "SUCCESS" else "ERROR"
            execRecord.finishedAt = LocalDateTime.now()
            execRecord.output = output
            executionLogRepository.save(execRecord)

            writeLogFile("task-${task.id}-${execution.id}", listOf(
                "[${LocalDateTime.now()}] Task '${task.name}' (${task.id}) — manual run",
                "[${LocalDateTime.now()}] Status: ${execRecord.status}",
                output["stdout"]?.toString() ?: output["body"]?.toString() ?: ""
            ))
        }

        return success(execution.id!!)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createPendingTaskExecution(parent: Execution, task: Task): UUID {
        val child = executionLogRepository.save(
            Execution(
                triggeredType = parent.triggeredType,
                type = "TASK",
                status = "PENDING",
                startedAt = LocalDateTime.now(),
                triggeredBy = parent.triggeredBy,
                task = task,
                workflow = parent.workflow,
                parentExecutionId = parent.id,
                taskName = task.name
            )
        )
        return child.id!!
    }

    private fun runTaskWithTracking(task: Task, childExecutionId: UUID, retryPolicy: Int = 0): Map<String, Any> {
        val child = executionLogRepository.findById(childExecutionId).get()
        child.status = "RUNNING"
        child.startedAt = LocalDateTime.now()
        executionLogRepository.save(child)

        var attempt = 0
        var lastOutput: Map<String, Any> = emptyMap()
        var success = false

        while (attempt <= retryPolicy && !success) {
            if (attempt > 0) {
                log.info("Task '{}' — retry attempt {}/{}", task.id, attempt, retryPolicy)
                child.retryCount = attempt
                executionLogRepository.save(child)
                Thread.sleep(2_000L)
            }

            val started = System.currentTimeMillis()
            try {
                lastOutput = runTask(task)
            } catch (ex: Exception) {
                enforceMinRunTime(started)
                lastOutput = mapOf(
                    "taskId" to task.id.toString(),
                    "taskName" to task.name,
                    "status" to "ERROR",
                    "error" to (ex.message ?: "Unexpected error")
                )
                attempt++
                continue
            }
            enforceMinRunTime(started)
            success = (lastOutput["status"] as? String) == "SUCCESS"
            attempt++
        }

        child.status = if (success) "SUCCESS" else "ERROR"
        child.retryCount = attempt - 1
        child.finishedAt = LocalDateTime.now()
        child.output = lastOutput
        executionLogRepository.save(child)
        return lastOutput
    }

    private fun enforceMinRunTime(startedMs: Long) {
        val elapsed = System.currentTimeMillis() - startedMs
        if (elapsed < MIN_TASK_RUNNING_MS) Thread.sleep(MIN_TASK_RUNNING_MS - elapsed)
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

    // ── Task dispatch (SCRIPT / HTTP) ─────────────────────────────────────────

    private fun runTask(task: Task): Map<String, Any> {
        return when (task.type.uppercase()) {
            "SCRIPT" -> runScriptTask(task)
            "HTTP"   -> runHttpTask(task)
            else     -> mapOf("taskId" to task.id.toString(), "type" to task.type, "status" to "SKIPPED", "reason" to "Unsupported task type")
        }
    }

    private fun runScriptTask(task: Task): Map<String, Any> {
        val config = task.config
        val command   = config["command"]   as? String ?: throw IllegalArgumentException("Task '${task.id}': missing 'command' in config")
        val fileName  = config["fileName"]  as? String ?: ""
        val directory = config["directory"] as? String
        val rawArgs   = config["args"]

        @Suppress("UNCHECKED_CAST")
        val argList: List<String> = when (rawArgs) {
            is List<*> -> rawArgs.filterIsInstance<String>()
            is String  -> rawArgs.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            else       -> emptyList()
        }

        val cmd = buildList {
            add(command)
            if (fileName.isNotBlank()) add(fileName)
            addAll(argList)
        }

        log.info("Task '{}' running script: {}", task.id, cmd)

        val pb = ProcessBuilder(cmd)
        if (directory != null) pb.directory(java.io.File(directory))
        pb.redirectErrorStream(true)

        val process = pb.start()
        val stdout  = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        log.info("Task '{}' script exited {} — output: {}", task.id, exitCode, stdout.take(200))

        return mapOf(
            "taskId"   to task.id.toString(),
            "taskName" to task.name,
            "type"     to "SCRIPT",
            "command"  to cmd.joinToString(" "),
            "exitCode" to exitCode,
            "stdout"   to stdout,
            "status"   to if (exitCode == 0) "SUCCESS" else "ERROR"
        )
    }

    private fun runHttpTask(task: Task): Map<String, Any> {
        val config = task.config
        val url    = config["url"]    as? String ?: throw IllegalArgumentException("Task '${task.id}': missing 'url' in config")
        val method = (config["method"] as? String ?: "GET").uppercase()

        log.info("Task '{}' running HTTP {} {}", task.id, method, url)

        val headers = HttpHeaders()
        @Suppress("UNCHECKED_CAST")
        val headersMap = config["headers"] as? Map<String, String> ?: emptyMap()
        headersMap.forEach { (k, v) -> headers.set(k, v) }

        val body = config["body"] as? String
        val entity = HttpEntity(body, headers)

        val response = restTemplate.exchange(url, HttpMethod.valueOf(method), entity, String::class.java)

        log.info("Task '{}' HTTP {} — status {}", task.id, url, response.statusCode.value())

        return mapOf(
            "taskId"     to task.id.toString(),
            "taskName"   to task.name,
            "type"       to "HTTP",
            "url"        to url,
            "method"     to method,
            "statusCode" to response.statusCode.value(),
            "body"       to (response.body ?: ""),
            "status"     to if (response.statusCode.is2xxSuccessful) "SUCCESS" else "ERROR"
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun findUser(username: String): User =
        userRepository.findByUsername(username)
            ?: throw NoSuchElementException("User '$username' not found")

    private fun findAccessibleWorkflow(workflowId: UUID, user: User): Workflow =
        if (isAdmin(user)) {
            workflowRepository.findById(workflowId).orElseThrow {
                NoSuchElementException("Workflow '$workflowId' not found")
            }
        } else {
            workflowRepository.findByIdAndOwnerId(workflowId, user.id!!)
                ?: throw NoSuchElementException("Workflow '$workflowId' not found")
        }

    private fun isAdmin(user: User): Boolean =
        user.role.name.equals("ADMIN", ignoreCase = true)
}

