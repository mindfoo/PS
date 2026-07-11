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
import org.workflow.repository.ExecutionRepository
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
    private val executionRepository: ExecutionRepository,
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
                         else workflowRepository.findAllPublic(userId)

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

        val workflow = findPublicWorkflow(workflowId, currentUser)
            ?: return failure(WorkflowError.WorkflowNotFound)

        workflow.name = request.name
        workflow.isPrivate = request.isPrivate
        return success(workflowRepository.save(workflow).toResponse())
    }

    @Transactional
    fun delete(workflowId: UUID, authenticationName: String): Either<WorkflowError, Unit> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val workflow = findPublicWorkflow(workflowId, currentUser)
            ?: return failure(WorkflowError.WorkflowNotFound)

        val wid = workflow.id ?: return failure(WorkflowError.WorkflowNotFound)
        /* Cascade-delete in FK dependency order: executions → schedules → workflow_tasks_order → tasks → workflow */
        executionRepository.deleteAllByWorkflowId(wid)
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

        val workflow = findPublicWorkflow(workflowId, currentUser)
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

        val workflow = findPublicWorkflow(workflowId, currentUser)
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
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(WorkflowError.UserNotFound)

        val ex = executionRepository.findByIdOrNull(executionId)
            ?: return failure(WorkflowError.ExecutionNotFound)

        if (!canViewExecution(ex, currentUser)) return failure(WorkflowError.AccessDenied)

        val exId = ex.id ?: return failure(WorkflowError.ExecutionNotFound)
        val taskExecs = executionRepository
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
        val executions = executionRepository.findTopLevelByWorkflowIdOrderByStartedAtDesc(wid)
            .map { ex ->
                val exId = ex.id ?: return failure(WorkflowError.WorkflowNotFound)
                val taskExecs = executionRepository
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

    /** Public workflows are accessible to everyone; private ones only to their owner and admins. */
    private fun findPublicWorkflow(workflowId: UUID, currentUser: org.workflow.entity.User): Workflow? {
        val workflow = workflowRepository.findByIdOrNull(workflowId) ?: return null
        return if (isPublic(workflow.isPrivate, workflow.createdBy.id, isAdmin(currentUser), currentUser.id)) workflow else null
    }

    /**
     * An execution is viewable when the caller triggered it, is an admin, or can access the
     * workflow/task it belongs to (public resource, or private resource they own).
     */
    private fun canViewExecution(execution: org.workflow.entity.Execution, currentUser: org.workflow.entity.User): Boolean {
        if (isAdmin(currentUser) || execution.triggeredBy.id == currentUser.id) return true

        val workflow = execution.workflow ?: execution.task?.workflow
        if (workflow != null) {
            return isPublic(workflow.isPrivate, workflow.createdBy.id, isAdmin(currentUser), currentUser.id)
        }
        val task = execution.task
        return task != null && isPublic(task.isPrivate, task.createdBy?.id, isAdmin(currentUser), currentUser.id)
    }

    private fun Workflow.toResponse(): WorkflowResponse {
        val lastStatus = id?.let { executionRepository.findLatestByWorkflowId(it)?.status }
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
