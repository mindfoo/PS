package org.workflow.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.workflow.dto.TaskCreateRequest
import org.workflow.dto.TaskResponse
import org.workflow.dto.TaskUpdateRequest
import org.workflow.entity.Task
import org.workflow.entity.User
import org.workflow.repository.TaskRepository
import org.workflow.repository.UserRepository
import org.workflow.repository.WorkflowRepository
import org.workflow.service.utils.TaskError
import org.workflow.utils.Either
import org.workflow.utils.failure
import org.workflow.utils.success
import java.util.UUID

@Service
/** Implements task CRUD operations constrained by workflow ownership. */
class TaskService(
    private val taskRepository: TaskRepository,
    private val workflowRepository: WorkflowRepository,
    private val userRepository: UserRepository
) {

    fun listByWorkflow(workflowId: UUID, authenticationName: String): Either<TaskError, List<TaskResponse>> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(TaskError.UserNotFound)

        val workflow = findAccessibleWorkflow(workflowId, currentUser)
            ?: return failure(TaskError.WorkflowNotFound)

        return success(taskRepository.findAllByWorkflowId(workflow.id!!).map { toResponse(it) })
    }

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

        val workflow = findAccessibleWorkflow(request.workflowId, currentUser)
            ?: return failure(TaskError.WorkflowNotFound)

        val saved = taskRepository.save(
            Task(
                name = request.name,
                type = request.type,
                config = request.config,
                workflow_id = workflow
            )
        )

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

        taskRepository.delete(task)
        return success(Unit)
    }

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
            workflowId = task.workflow_id.id
        )
}
