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
import org.workflow.repository.UserRepository
import org.workflow.repository.WorkflowRepository
import org.workflow.repository.WorkflowTaskOrderRepository
import org.workflow.service.utils.TaskError
import org.workflow.utils.Either
import org.workflow.utils.failure
import org.workflow.utils.success
import java.util.UUID

@Service
/** Implements task CRUD and workflow-link operations. Tasks are standalone and linked via WorkflowTaskOrder. */
class TaskService(
    private val taskRepository: TaskRepository,
    private val workflowRepository: WorkflowRepository,
    private val userRepository: UserRepository,
    private val workflowTaskOrderRepository: WorkflowTaskOrderRepository
) {

    // ── List ─────────────────────────────────────────────────────────────────

    fun listAll(authenticationName: String): Either<TaskError, List<TaskResponse>> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(TaskError.UserNotFound)

        val tasks = if (isAdmin(currentUser)) {
            taskRepository.findAll()
        } else {
            taskRepository.findAllByCreatedById(currentUser.id!!)
        }
        return success(tasks.map { toResponse(it) })
    }

    fun listByWorkflow(workflowId: UUID, authenticationName: String): Either<TaskError, List<WorkflowTaskEntry>> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(TaskError.UserNotFound)

        val workflow = findAccessibleWorkflow(workflowId, currentUser)
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
                dependsOnTaskId = wto.dependsOnTask?.id
            )
        }

        // Include tasks still linked via legacy workflow_id FK but without an order row
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
                    dependsOnTaskId = null
                )
            }

        return success(ordered + unordered)
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    fun getById(taskId: UUID, authenticationName: String): Either<TaskError, TaskResponse> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(TaskError.UserNotFound)

        val task = if (isAdmin(currentUser)) {
            taskRepository.findById(taskId).orElse(null)
        } else {
            taskRepository.findByIdAndOwnerId(taskId, currentUser.id!!)
        } ?: return failure(TaskError.TaskNotFound)

        return success(toResponse(task))
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
                workflow_id = workflow,
                createdBy = currentUser
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

    // ── Workflow linking ──────────────────────────────────────────────────────

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

        val existing = workflowTaskOrderRepository.findByWorkflowIdAndTaskId(workflow.id!!, task.id!!)
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

        val rows = workflowTaskOrderRepository.findByWorkflowIdAndTaskId(workflow.id!!, task.id!!)
        if (rows.isEmpty()) return failure(TaskError.NotLinked)

        workflowTaskOrderRepository.deleteAll(rows)
        return success(Unit)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun findAccessibleWorkflow(workflowId: UUID, user: User) =
        if (isAdmin(user)) {
            workflowRepository.findById(workflowId).orElse(null)
        } else {
            workflowRepository.findByIdAndOwnerId(workflowId, user.id!!)
        }

    private fun findCurrentUser(username: String): User? =
        userRepository.findByUsername(username)

    private fun isAdmin(user: User): Boolean =
        user.role.name.equals("ADMIN", ignoreCase = true)

    private fun toResponse(task: Task): TaskResponse =
        TaskResponse(
            id = task.id,
            name = task.name,
            type = task.type,
            config = task.config,
            workflowId = task.workflow_id?.id
        )
}
