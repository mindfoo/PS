package org.workflow.service

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.workflow.dto.ExecutionSummaryResponse
import org.workflow.dto.TaskExecutionSummary
import org.workflow.dto.RetryPolicyUpdateRequest
import org.workflow.dto.TaskReorderRequest
import org.workflow.dto.WorkflowCreateRequest
import org.workflow.dto.WorkflowResponse
import org.workflow.dto.WorkflowUpdateRequest
import org.workflow.entity.Workflow
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

/** Implements workflow CRUD operations with ownership and admin visibility rules. */
@Service
class WorkflowService(
    private val workflowRepository: WorkflowRepository,
    private val workflowTaskOrderRepository: WorkflowTaskOrderRepository,
    private val executionLogRepository: ExecutionLogRepository,
    private val scheduleRepository: ScheduleRepository,
    private val taskRepository: TaskRepository,
    private val helpers: ServiceHelpers
) {

    @Transactional(readOnly = true)
    fun list(authenticationName: String): Either<WorkflowError, List<WorkflowResponse>> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val userId = currentUser.id ?: return failure(WorkflowError.UserNotFound)
        val workflows = if (isAdmin(currentUser)) workflowRepository.findAll()
                         else workflowRepository.findAllVisible(userId)
        return success(workflows.map { it.toResponse() })
    }

    @Transactional(readOnly = true)
    fun getById(workflowId: UUID, authenticationName: String): Either<WorkflowError, WorkflowResponse> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val workflow = workflowRepository.findByIdOrNull(workflowId)
            ?: return failure(WorkflowError.WorkflowNotFound)
        if (!isAdmin(currentUser) && workflow.isPrivate && workflow.createdBy.id != currentUser.id) {
            return failure(WorkflowError.AccessDenied)
        }
        return success(workflow.toResponse())
    }

    @Transactional
    fun create(request: WorkflowCreateRequest, authenticationName: String): Either<WorkflowError, WorkflowResponse> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val saved = workflowRepository.save(
            Workflow(
                name = request.name,
                createdBy = currentUser,
                isPrivate = request.isPrivate
            )
        )

        return success(saved.toResponse())
    }

    @Transactional
    fun update(
        workflowId: UUID,
        request: WorkflowUpdateRequest,
        authenticationName: String
    ): Either<WorkflowError, WorkflowResponse> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val userId = currentUser.id ?: return failure(WorkflowError.UserNotFound)
        val workflow = findOwnedWorkflow(workflowId, currentUser, userId)
            ?: return failure(WorkflowError.WorkflowNotFound)

        workflow.name = request.name
        workflow.isPrivate = request.isPrivate
        return success(workflowRepository.save(workflow).toResponse())
    }

    @Transactional
    fun delete(workflowId: UUID, authenticationName: String): Either<WorkflowError, Unit> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val userId = currentUser.id ?: return failure(WorkflowError.UserNotFound)
        val workflow = findOwnedWorkflow(workflowId, currentUser, userId)
            ?: return failure(WorkflowError.WorkflowNotFound)

        val wid = workflow.id ?: return failure(WorkflowError.WorkflowNotFound)
        /* Cascade-delete in FK dependency order: executions → schedules → workflow_tasks_order → tasks → workflow */
        executionLogRepository.deleteAllByWorkflowId(wid)
        scheduleRepository.deleteAllByWorkflowId(wid)
        workflowTaskOrderRepository.deleteAllByWorkflowId(wid)
        taskRepository.deleteAllByWorkflowId(wid)

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

        val userId = currentUser.id ?: return failure(WorkflowError.UserNotFound)
        val workflow = findOwnedWorkflow(workflowId, currentUser, userId)
            ?: return failure(WorkflowError.WorkflowNotFound)

        val wid = workflow.id ?: return failure(WorkflowError.WorkflowNotFound)
        val orderRows = workflowTaskOrderRepository.findAllByWorkflowIdOrderByTaskOrderAsc(wid)
            .associateBy { it.id ?: return failure(WorkflowError.WorkflowNotFound) }

        request.items.forEach { item ->
            val row = orderRows[item.orderId]
                ?: return failure(WorkflowError.WorkflowNotFound)
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

        val userId = currentUser.id ?: return failure(WorkflowError.UserNotFound)
        val workflow = findOwnedWorkflow(workflowId, currentUser, userId)
            ?: return failure(WorkflowError.WorkflowNotFound)

        val wid = workflow.id ?: return failure(WorkflowError.WorkflowNotFound)
        val orderRow = workflowTaskOrderRepository.findByWorkflowIdAndTaskId(wid, taskId)
            ?: return failure(WorkflowError.TaskNotLinked)

        orderRow.retryPolicy = request.retryPolicy
        workflowTaskOrderRepository.save(orderRow)
        return success(Unit)
    }

    @Transactional(readOnly = true)
    fun getExecution(executionId: UUID, authenticationName: String): Either<WorkflowError, ExecutionSummaryResponse> {
        // Any authenticated user who can read workflows can poll execution status
        findCurrentUser(authenticationName) ?: return failure(WorkflowError.UserNotFound)

        val ex = executionLogRepository.findByIdOrNull(executionId)
            ?: return failure(WorkflowError.ExecutionNotFound)

        val exId = ex.id ?: return failure(WorkflowError.ExecutionNotFound)
        val taskExecs = executionLogRepository
            .findAllByParentExecutionIdOrderByStartedAtAsc(exId)
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

        val workflow = workflowRepository.findByIdOrNull(workflowId)
            ?: return failure(WorkflowError.WorkflowNotFound)

        val wid = workflow.id ?: return failure(WorkflowError.WorkflowNotFound)
        val executions = executionLogRepository.findTopLevelByWorkflowIdOrderByStartedAtDesc(wid)
            .map { ex ->
                val exId = ex.id ?: return failure(WorkflowError.WorkflowNotFound)
                val taskExecs = executionLogRepository
                    .findAllByParentExecutionIdOrderByStartedAtAsc(exId)
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

    /** Admins can access any workflow by ID; other users only their own. */
    private fun findOwnedWorkflow(workflowId: UUID, currentUser: org.workflow.entity.User, userId: UUID): Workflow? =
        if (isAdmin(currentUser)) workflowRepository.findByIdOrNull(workflowId)
        else workflowRepository.findByIdAndOwnerId(workflowId, userId)

    private fun Workflow.toResponse(): WorkflowResponse {
        val lastStatus = id?.let { executionLogRepository.findLatestByWorkflowId(it)?.status }
        return WorkflowResponse(
            id = id,
            name = name,
            ownerId = createdBy.id,
            ownerUsername = createdBy.username,
            lastRunStatus = lastStatus,
            isPrivate = isPrivate
        )
    }
}
