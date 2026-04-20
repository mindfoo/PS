package org.workflow.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.workflow.entity.Execution
import org.workflow.entity.User
import org.workflow.entity.Workflow
import org.workflow.repository.ExecutionLogRepository
import org.workflow.repository.TaskRepository
import org.workflow.repository.UserRepository
import org.workflow.repository.WorkflowRepository
import org.workflow.repository.WorkflowTaskOrderRepository
import java.time.LocalDateTime
import java.util.UUID

@Service
/** Creates and runs workflow executions, including manual and cron-triggered flows. */
class ExecutionService(
    private val executionLogRepository: ExecutionLogRepository,
    private val workflowRepository: WorkflowRepository,
    private val workflowTaskOrderRepository: WorkflowTaskOrderRepository,
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
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

        runExecution(execution.id!!)
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

    @Transactional
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

        try {
            val orderedTasks = workflowTaskOrderRepository.findAllByWorkflowIdOrderByTaskOrderAsc(workflow.id!!)
                .map { it.task }
                .ifEmpty { taskRepository.findAllByWorkflowId(workflow.id!!) }

            orderedTasks.forEach { task ->
                log.info("Execution {} running task {} ({})", execution.id, task.id, task.name)
            }

            execution.status = "SUCCESS"
            execution.finishedAt = LocalDateTime.now()
            execution.output = mapOf(
                "workflowId" to workflow.id.toString(),
                "tasksExecuted" to orderedTasks.size,
                "message" to "Execution completed"
            )
        } catch (ex: Exception) {
            execution.status = "ERROR"
            execution.retryCount += 1
            execution.finishedAt = LocalDateTime.now()
            execution.output = mapOf(
                "error" to (ex.message ?: "Unknown execution error"),
                "retryCount" to execution.retryCount
            )
            log.error("Execution {} failed: {}", execution.id, ex.message, ex)
        }

        executionLogRepository.save(execution)
    }

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

