package org.workflow.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.workflow.dto.TaskCreateRequest
import org.workflow.dto.TaskResponse
import org.workflow.dto.TaskUpdateRequest
import org.workflow.dto.WorkflowTaskEntry
import org.workflow.entity.Task
import org.workflow.entity.User
import org.workflow.entity.WorkflowTaskOrder
import org.workflow.repository.TaskRepository
import org.workflow.repository.WorkflowRepository
import org.workflow.repository.WorkflowTaskOrderRepository
import org.workflow.service.utils.TaskError
import org.workflow.utils.Either
import org.workflow.utils.failure
import org.workflow.utils.success
import java.util.UUID

/** Implements task CRUD and workflow-link operations. Tasks are standalone and linked via WorkflowTaskOrder. */
@Service
class TaskService(
    private val taskRepository: TaskRepository,
    private val workflowRepository: WorkflowRepository,
    private val workflowTaskOrderRepository: WorkflowTaskOrderRepository,
    private val helpers: ServiceHelpers
) {

    // List

    @Transactional(readOnly = true)
    fun listAll(authenticationName: String): Either<TaskError, List<TaskResponse>> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(TaskError.UserNotFound)

        val tasks = if (isAdmin(currentUser)) taskRepository.findAll()
                     else taskRepository.findAllVisible(currentUser.id!!)
        val taskIds = tasks.mapNotNull { it.id }.toSet()
        val linkedWorkflowByTaskId: Map<UUID, UUID?> = if (taskIds.isNotEmpty()) {
            workflowTaskOrderRepository.findAllByTaskIdIn(taskIds)
                .groupBy { it.task.id!! }
                .mapValues { (_, wtos) -> wtos.firstOrNull()?.workflow?.id }
        } else emptyMap()
        return success(tasks.map { task ->
            toResponse(task, task.workflow?.id ?: linkedWorkflowByTaskId[task.id])
        })
    }

    @Transactional(readOnly = true)
    fun listByWorkflow(workflowId: UUID, authenticationName: String): Either<TaskError, List<WorkflowTaskEntry>> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(TaskError.UserNotFound)

        val workflow = workflowRepository.findById(workflowId).orElse(null)
            ?: return failure(TaskError.WorkflowNotFound)

        val orderRows = workflowTaskOrderRepository.findAllByWorkflowIdOrderByTaskOrderAsc(workflow.id!!)

        val ordered = orderRows.map { wto ->
            WorkflowTaskEntry(
                taskId = wto.task.id,
                name = wto.task.name,
                type = wto.task.type,
                config = wto.task.config,
                orderId = wto.id,
                taskOrder = wto.taskOrder,
                retryPolicy = wto.retryPolicy,
                dependsOnTaskId = wto.dependsOnTask?.id,
                isPrivate = wto.task.isPrivate
            )
        }

        // Include tasks linked via direct workflow FK that have no WorkflowTaskOrder row yet
        val orderedTaskIds = orderRows.map { it.task.id }.toSet()
        val nextStage = (orderRows.maxOfOrNull { it.taskOrder } ?: 0) + 1
        val unordered = taskRepository.findAllByWorkflowId(workflow.id!!)
            .filter { it.id !in orderedTaskIds }
            .mapIndexed { idx, task ->
                WorkflowTaskEntry(
                    taskId = task.id,
                    name = task.name,
                    type = task.type,
                    config = task.config,
                    orderId = null,
                    taskOrder = nextStage + idx,
                    retryPolicy = 0,
                    dependsOnTaskId = null,
                    isPrivate = task.isPrivate
                )
            }

        return success(ordered + unordered)
    }

    // CRUD

    @Transactional(readOnly = true)
    fun getById(taskId: UUID, authenticationName: String): Either<TaskError, TaskResponse> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(TaskError.UserNotFound)

        val task = taskRepository.findById(taskId).orElse(null)
            ?: return failure(TaskError.TaskNotFound)
        if (!isAdmin(currentUser) && task.isPrivate && task.createdBy?.id != currentUser.id) {
            return failure(TaskError.AccessDenied)
        }

        val effectiveWorkflowId = task.workflow?.id
            ?: workflowTaskOrderRepository.findAllByTaskId(task.id!!).firstOrNull()?.workflow?.id
        return success(toResponse(task, effectiveWorkflowId))
    }

    @Transactional
    fun create(request: TaskCreateRequest, authenticationName: String): Either<TaskError, TaskResponse> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(TaskError.UserNotFound)

        val workflow = if (request.workflowId != null) {
            findAccessibleWorkflow(request.workflowId, currentUser)
                ?: return failure(TaskError.WorkflowNotFound)
        } else null

        val saved = taskRepository.save(
            Task(
                name = request.name,
                type = request.type,
                config = request.config,
                workflow = workflow,
                createdBy = currentUser,
                isPrivate = request.isPrivate
            )
        )

        if (workflow != null) {
            val nextStage = workflowTaskOrderRepository
                .findAllByWorkflowIdOrderByTaskOrderAsc(workflow.id!!)
                .maxOfOrNull { it.taskOrder }?.plus(1) ?: 1

            workflowTaskOrderRepository.save(
                WorkflowTaskOrder(workflow = workflow, task = saved, taskOrder = nextStage)
            )
        }

        return success(toResponse(saved))
    }

    @Transactional
    fun update(taskId: UUID, request: TaskUpdateRequest, authenticationName: String): Either<TaskError, TaskResponse> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(TaskError.UserNotFound)

        val task = if (isAdmin(currentUser)) {
            taskRepository.findById(taskId).orElse(null)
        } else {
            taskRepository.findByIdAndOwnerId(taskId, currentUser.id!!)
        } ?: return failure(TaskError.TaskNotFound)

        task.name = request.name
        task.type = request.type
        task.config = request.config
        task.isPrivate = request.isPrivate

        return success(toResponse(taskRepository.save(task)))
    }

    @Transactional
    fun delete(taskId: UUID, authenticationName: String): Either<TaskError, Unit> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(TaskError.UserNotFound)

        val task = if (isAdmin(currentUser)) {
            taskRepository.findById(taskId).orElse(null)
        } else {
            taskRepository.findByIdAndOwnerId(taskId, currentUser.id!!)
        } ?: return failure(TaskError.TaskNotFound)

        workflowTaskOrderRepository.deleteAll(workflowTaskOrderRepository.findAllByTaskId(task.id!!))
        taskRepository.delete(task)
        return success(Unit)
    }

    // Workflow linking

    @Transactional
    fun linkToWorkflow(taskId: UUID, workflowId: UUID, authenticationName: String): Either<TaskError, Unit> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(TaskError.UserNotFound)

        val task = if (isAdmin(currentUser)) {
            taskRepository.findById(taskId).orElse(null)
        } else {
            taskRepository.findByIdAndOwnerId(taskId, currentUser.id!!)
        } ?: return failure(TaskError.TaskNotFound)

        val workflow = findAccessibleWorkflow(workflowId, currentUser)
            ?: return failure(TaskError.WorkflowNotFound)

        val existing = workflowTaskOrderRepository.findAllByWorkflowIdAndTaskId(workflow.id!!, task.id!!)
        if (existing.isNotEmpty()) return failure(TaskError.AlreadyLinked)

        val nextStage = workflowTaskOrderRepository
            .findAllByWorkflowIdOrderByTaskOrderAsc(workflow.id!!)
            .maxOfOrNull { it.taskOrder }?.plus(1) ?: 1

        workflowTaskOrderRepository.save(
            WorkflowTaskOrder(workflow = workflow, task = task, taskOrder = nextStage)
        )
        return success(Unit)
    }

    @Transactional
    fun unlinkFromWorkflow(taskId: UUID, workflowId: UUID, authenticationName: String): Either<TaskError, Unit> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(TaskError.UserNotFound)

        val task = if (isAdmin(currentUser)) {
            taskRepository.findById(taskId).orElse(null)
        } else {
            taskRepository.findByIdAndOwnerId(taskId, currentUser.id!!)
        } ?: return failure(TaskError.TaskNotFound)

        val workflow = findAccessibleWorkflow(workflowId, currentUser)
            ?: return failure(TaskError.WorkflowNotFound)

        val rows = workflowTaskOrderRepository.findAllByWorkflowIdAndTaskId(workflow.id!!, task.id!!)
        if (rows.isEmpty()) return failure(TaskError.NotLinked)

        workflowTaskOrderRepository.deleteAll(rows)

        // Clear the direct FK if it still points to this workflow (set when task was created inside it)
        if (task.workflow?.id == workflow.id) {
            task.workflow = null
            taskRepository.save(task)
        }

        return success(Unit)
    }

    // Helpers

    private fun findAccessibleWorkflow(workflowId: UUID, user: User) =
        if (isAdmin(user)) {
            workflowRepository.findById(workflowId).orElse(null)
        } else {
            workflowRepository.findByIdAndOwnerId(workflowId, user.id!!)
        }

    private fun findCurrentUser(username: String) = helpers.findUser(username)
    private fun isAdmin(user: User) = helpers.isAdmin(user)

    private fun toResponse(task: Task, effectiveWorkflowId: UUID? = task.workflow?.id): TaskResponse =
        TaskResponse(
            id = task.id,
            name = task.name,
            type = task.type,
            config = task.config,
            workflowId = effectiveWorkflowId,
            isPrivate = task.isPrivate
        )
}
