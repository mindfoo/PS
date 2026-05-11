package org.workflow.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.workflow.dto.ExecutionSummaryResponse
import org.workflow.dto.TaskExecutionSummary
import org.workflow.dto.RetryPolicyUpdateRequest
import org.workflow.dto.TaskReorderRequest
import org.workflow.dto.WorkflowCreateRequest
import org.workflow.dto.WorkflowResponse
import org.workflow.dto.WorkflowUpdateRequest
import org.workflow.entity.User
import org.workflow.entity.Workflow
import org.workflow.repository.AlertRepository
import org.workflow.repository.ExecutionLogRepository
import org.workflow.repository.ScheduleRepository
import org.workflow.repository.TaskRepository
import org.workflow.repository.WorkflowRepository
import org.workflow.repository.WorkflowTaskOrderRepository
import org.workflow.service.utils.WorkflowError
import org.workflow.utils.Either
import org.workflow.utils.failure
import org.workflow.utils.success
import java.util.UUID

@Service
/** Implements workflow CRUD operations with ownership and admin visibility rules. */
class WorkflowService(
    private val workflowRepository: WorkflowRepository,
    private val workflowTaskOrderRepository: WorkflowTaskOrderRepository,
    private val executionLogRepository: ExecutionLogRepository,
    private val scheduleRepository: ScheduleRepository,
    private val taskRepository: TaskRepository,
    private val alertRepository: AlertRepository,
    private val helpers: ServiceHelpers
) {

    @Transactional(readOnly = true)
    fun list(authenticationName: String): Either<WorkflowError, List<WorkflowResponse>> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val workflows = if (isAdmin(currentUser)) {
            workflowRepository.findAll()
        } else {
            workflowRepository.findAllByOwnerId(currentUser.id!!)
        }

        return success(workflows.map { toResponse(it) })
    }

    @Transactional(readOnly = true)
    fun getById(workflowId: UUID, authenticationName: String): Either<WorkflowError, WorkflowResponse> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val workflow = if (isAdmin(currentUser)) {
            workflowRepository.findById(workflowId).orElse(null)
        } else {
            workflowRepository.findByIdAndOwnerId(workflowId, currentUser.id!!)
        } ?: return failure(WorkflowError.WorkflowNotFound)

        return success(toResponse(workflow))
    }

    @Transactional
    fun create(request: WorkflowCreateRequest, authenticationName: String): Either<WorkflowError, WorkflowResponse> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val saved = workflowRepository.save(
            Workflow(
                name = request.name,
                createdBy = currentUser
            )
        )

        return success(toResponse(saved))
    }

    @Transactional
    fun update(
        workflowId: UUID,
        request: WorkflowUpdateRequest,
        authenticationName: String
    ): Either<WorkflowError, WorkflowResponse> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val workflow = if (isAdmin(currentUser)) {
            workflowRepository.findById(workflowId).orElse(null)
        } else {
            workflowRepository.findByIdAndOwnerId(workflowId, currentUser.id!!)
        } ?: return failure(WorkflowError.WorkflowNotFound)

        workflow.name = request.name
        return success(toResponse(workflowRepository.save(workflow)))
    }

    @Transactional
    fun delete(workflowId: UUID, authenticationName: String): Either<WorkflowError, Unit> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val workflow = if (isAdmin(currentUser)) {
            workflowRepository.findById(workflowId).orElse(null)
        } else {
            workflowRepository.findByIdAndOwnerId(workflowId, currentUser.id!!)
        } ?: return failure(WorkflowError.WorkflowNotFound)

        // Cascade-delete in FK dependency order:
        // alerts → executions → schedules → workflow_tasks_order → tasks → workflow
        alertRepository.deleteAllByWorkflowId(workflow.id!!)
        executionLogRepository.deleteAllByWorkflowId(workflow.id!!)
        scheduleRepository.deleteAllByWorkflowId(workflow.id!!)
        workflowTaskOrderRepository.deleteAllByWorkflowId(workflow.id!!)
        taskRepository.deleteAllByWorkflowId(workflow.id!!)

        workflowRepository.delete(workflow)
        return success(Unit)
    }

    /**
     * Reorders tasks within a workflow by updating the taskOrder on each WorkflowTaskOrder row.
     * Tasks sharing the same taskOrder value will be executed in parallel.
     */
    @Transactional
    fun reorderTasks(workflowId: UUID, request: TaskReorderRequest, authenticationName: String): Either<WorkflowError, Unit> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val workflow = if (isAdmin(currentUser)) {
            workflowRepository.findById(workflowId).orElse(null)
        } else {
            workflowRepository.findByIdAndOwnerId(workflowId, currentUser.id!!)
        } ?: return failure(WorkflowError.WorkflowNotFound)

        val orderRows = workflowTaskOrderRepository.findAllByWorkflowIdOrderByTaskOrderAsc(workflow.id!!)
            .associateBy { it.id!! }

        request.items.forEach { item ->
            val row = orderRows[item.orderId]
                ?: throw NoSuchElementException("WorkflowTaskOrder '${item.orderId}' not found in workflow '${workflowId}'")
            row.taskOrder = item.taskOrder
        }

        workflowTaskOrderRepository.saveAll(orderRows.values)
        return success(Unit)
    }

    @Transactional
    fun updateRetryPolicy(
        workflowId: UUID,
        taskId: UUID,
        request: RetryPolicyUpdateRequest,
        authenticationName: String
    ): Either<WorkflowError, Unit> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val workflow = if (isAdmin(currentUser)) {
            workflowRepository.findById(workflowId).orElse(null)
        } else {
            workflowRepository.findByIdAndOwnerId(workflowId, currentUser.id!!)
        } ?: return failure(WorkflowError.WorkflowNotFound)

        val orderRow = workflowTaskOrderRepository.findByWorkflowIdAndTaskId(workflow.id!!, taskId)
            ?: return failure(WorkflowError.WorkflowNotFound)

        orderRow.retryPolicy = request.retryPolicy
        workflowTaskOrderRepository.save(orderRow)
        return success(Unit)
    }

    @Transactional(readOnly = true)
    fun getExecution(executionId: UUID, authenticationName: String): Either<WorkflowError, ExecutionSummaryResponse> {
        // Any authenticated user who can read workflows can poll execution status
        findCurrentUser(authenticationName) ?: return failure(WorkflowError.UserNotFound)

        val ex = executionLogRepository.findById(executionId).orElse(null)
            ?: return failure(WorkflowError.WorkflowNotFound)

        val taskExecs = executionLogRepository
            .findAllByParentExecutionIdOrderByStartedAtAsc(ex.id!!)
            .map { child ->
                TaskExecutionSummary(
                    executionId = child.id,
                    taskId = child.task?.id,
                    taskName = child.taskName ?: child.task?.name,
                    status = child.status,
                    startedAt = child.startedAt,
                    finishedAt = child.finishedAt,
                    output = child.output
                )
            }

        return success(
            ExecutionSummaryResponse(
                id = ex.id,
                triggeredType = ex.triggeredType,
                type = ex.type,
                status = ex.status,
                startedAt = ex.startedAt,
                finishedAt = ex.finishedAt,
                triggeredBy = ex.triggeredBy.username,
                retryCount = ex.retryCount,
                output = ex.output,
                taskExecutions = taskExecs.ifEmpty { null }
            )
        )
    }

    @Transactional(readOnly = true)
    fun listExecutions(workflowId: UUID, authenticationName: String): Either<WorkflowError, List<ExecutionSummaryResponse>> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val workflow = if (isAdmin(currentUser)) {
            workflowRepository.findById(workflowId).orElse(null)
        } else {
            workflowRepository.findByIdAndOwnerId(workflowId, currentUser.id!!)
        } ?: return failure(WorkflowError.WorkflowNotFound)

        val executions = executionLogRepository.findTopLevelByWorkflowIdOrderByStartedAtDesc(workflow.id!!)
            .map { ex ->
                val taskExecs = executionLogRepository
                    .findAllByParentExecutionIdOrderByStartedAtAsc(ex.id!!)
                    .map { child ->
                        TaskExecutionSummary(
                            executionId = child.id,
                            taskId = child.task?.id,
                            taskName = child.taskName ?: child.task?.name,
                            status = child.status,
                            startedAt = child.startedAt,
                            finishedAt = child.finishedAt,
                            output = child.output
                        )
                    }
                ExecutionSummaryResponse(
                    id = ex.id,
                    triggeredType = ex.triggeredType,
                    type = ex.type,
                    status = ex.status,
                    startedAt = ex.startedAt,
                    finishedAt = ex.finishedAt,
                    triggeredBy = ex.triggeredBy.username,
                    retryCount = ex.retryCount,
                    output = ex.output,
                    taskExecutions = taskExecs.ifEmpty { null }
                )
            }

        return success(executions)
    }

    private fun findCurrentUser(username: String) = helpers.findUser(username)
    private fun isAdmin(user: org.workflow.entity.User) = helpers.isAdmin(user)

    private fun toResponse(workflow: Workflow): WorkflowResponse {
        val lastStatus = executionLogRepository.findLatestByWorkflowId(workflow.id!!)?.status
        return WorkflowResponse(
            id = workflow.id,
            name = workflow.name,
            ownerId = workflow.createdBy.id,
            ownerUsername = workflow.createdBy.username,
            lastRunStatus = lastStatus
        )
    }
}
