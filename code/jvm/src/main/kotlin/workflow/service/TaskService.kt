package org.workflow.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.workflow.dto.ScriptInfoResponse
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
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

/** Implements task CRUD, workflow-link operations, and script upload management. */
@Service
class TaskService(
    private val taskRepository: TaskRepository,
    private val workflowRepository: WorkflowRepository,
    private val workflowTaskOrderRepository: WorkflowTaskOrderRepository,
    private val helpers: ServiceHelpers,
    @Value("\${app.scripts.base-dir:./scripts}") private val scriptsBaseDir: String,
    @Value("\${app.scripts.max-size-mb:10}") private val maxScriptSizeMb: Long
) {

    companion object {
        private val ALLOWED_EXTENSIONS = setOf("py", "js", "ts", "sh", "bash")
    }

    // List

    @Transactional(readOnly = true)
    fun listAll(authenticationName: String): Either<TaskError, List<TaskResponse>> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(TaskError.UserNotFound)

        val userId = currentUser.id ?: return failure(TaskError.UserNotFound)
        val tasks = if (isAdmin(currentUser)) taskRepository.findAll()
                     else taskRepository.findAllVisible(userId)
        val taskIds = tasks.mapNotNull { it.id }.toSet()
        val linkedWorkflowByTaskId: Map<UUID, UUID?> = if (taskIds.isNotEmpty()) {
            workflowTaskOrderRepository.findAllByTaskIdIn(taskIds)
                .mapNotNull { wto -> wto.task.id?.let { id -> id to wto } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, wtos) -> wtos.firstOrNull()?.workflow?.id }
        } else emptyMap()
        return success(tasks.map { task ->
            task.toResponse(task.workflow?.id ?: linkedWorkflowByTaskId[task.id])
        })
    }

    @Transactional(readOnly = true)
    fun listByWorkflow(workflowId: UUID, authenticationName: String): Either<TaskError, List<WorkflowTaskEntry>> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(TaskError.UserNotFound)

        val workflow = workflowRepository.findByIdOrNull(workflowId)
            ?: return failure(TaskError.WorkflowNotFound)

        val wfId = workflow.id ?: return failure(TaskError.WorkflowNotFound)
        val orderRows = workflowTaskOrderRepository.findAllByWorkflowIdOrderByTaskOrderAsc(wfId)

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
        val unordered = taskRepository.findAllByWorkflowId(wfId)
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

        val task = taskRepository.findByIdOrNull(taskId)
            ?: return failure(TaskError.TaskNotFound)
        if (!isAdmin(currentUser) && task.isPrivate && task.createdBy?.id != currentUser.id) {
            return failure(TaskError.AccessDenied)
        }

        val effectiveWorkflowId = task.workflow?.id
            ?: task.id?.let { workflowTaskOrderRepository.findAllByTaskId(it).firstOrNull()?.workflow?.id }
        return success(task.toResponse(effectiveWorkflowId))
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
            val wfId = workflow.id ?: return failure(TaskError.WorkflowNotFound)
            val nextStage = workflowTaskOrderRepository
                .findAllByWorkflowIdOrderByTaskOrderAsc(wfId)
                .maxOfOrNull { it.taskOrder }?.plus(1) ?: 1

            workflowTaskOrderRepository.save(
                WorkflowTaskOrder(workflow = workflow, task = saved, taskOrder = nextStage)
            )
        }

        return success(saved.toResponse())
    }

    @Transactional
    fun update(taskId: UUID, request: TaskUpdateRequest, authenticationName: String): Either<TaskError, TaskResponse> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(TaskError.UserNotFound)

        val task = findOwnedTask(taskId, currentUser)
            ?: return failure(TaskError.TaskNotFound)

        task.name = request.name
        task.type = request.type
        task.config = request.config
        task.isPrivate = request.isPrivate

        return success(taskRepository.save(task).toResponse())
    }

    @Transactional
    fun delete(taskId: UUID, authenticationName: String): Either<TaskError, Unit> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(TaskError.UserNotFound)

        val task = findOwnedTask(taskId, currentUser)
            ?: return failure(TaskError.TaskNotFound)

        val tId = task.id ?: return failure(TaskError.TaskNotFound)
        workflowTaskOrderRepository.deleteAll(workflowTaskOrderRepository.findAllByTaskId(tId))
        taskRepository.delete(task)
        return success(Unit)
    }

    // Workflow linking

    @Transactional
    fun linkToWorkflow(taskId: UUID, workflowId: UUID, authenticationName: String): Either<TaskError, Unit> {
        val currentUser = findCurrentUser(authenticationName)
            ?: return failure(TaskError.UserNotFound)

        val task = findOwnedTask(taskId, currentUser)
            ?: return failure(TaskError.TaskNotFound)

        val workflow = findAccessibleWorkflow(workflowId, currentUser)
            ?: return failure(TaskError.WorkflowNotFound)

        val wfId = workflow.id ?: return failure(TaskError.WorkflowNotFound)
        val tId = task.id ?: return failure(TaskError.TaskNotFound)
        val existing = workflowTaskOrderRepository.findAllByWorkflowIdAndTaskId(wfId, tId)
        if (existing.isNotEmpty()) return failure(TaskError.AlreadyLinked)

        val nextStage = workflowTaskOrderRepository
            .findAllByWorkflowIdOrderByTaskOrderAsc(wfId)
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

        val task = findOwnedTask(taskId, currentUser)
            ?: return failure(TaskError.TaskNotFound)

        val workflow = findAccessibleWorkflow(workflowId, currentUser)
            ?: return failure(TaskError.WorkflowNotFound)

        val wfId = workflow.id ?: return failure(TaskError.WorkflowNotFound)
        val tId = task.id ?: return failure(TaskError.TaskNotFound)
        val rows = workflowTaskOrderRepository.findAllByWorkflowIdAndTaskId(wfId, tId)
        if (rows.isEmpty()) return failure(TaskError.NotLinked)

        workflowTaskOrderRepository.deleteAll(rows)

        // Clear the direct FK if it still points to this workflow (set when task was created inside it)
        if (task.workflow?.id == workflow.id) {
            task.workflow = null
            taskRepository.save(task)
        }

        return success(Unit)
    }

    // Script upload

    /**
     * Saves the uploaded file to [scriptsBaseDir]/{taskId}/ and updates the task's config so
     * the executor picks it up automatically on the next run.
     * Only ADMIN and DEV (task:upload authority) reach this method; access is enforced by the controller.
     */
    @Transactional
    fun uploadScript(
        taskId: UUID,
        file: MultipartFile,
        authenticationName: String
    ): Either<TaskError, ScriptInfoResponse> {
        val currentUser = findCurrentUser(authenticationName) ?: return failure(TaskError.UserNotFound)
        val task = taskRepository.findByIdOrNull(taskId) ?: return failure(TaskError.TaskNotFound)

        if (task.isPrivate && !isAdmin(currentUser) && task.createdBy?.id != currentUser.id) {
            return failure(TaskError.AccessDenied)
        }

        val originalName = file.originalFilename?.takeIf { it.isNotBlank() }
            ?: return failure(TaskError.InvalidFileType)

        /* Strip any path components — prevents path traversal attacks.
           A name like "../../etc/evil.py" would pass extension check but resolve outside scriptsBaseDir.
           Paths.get(...).fileName gives only the last segment, e.g. "evil.py". */
        val safeName = Paths.get(originalName).fileName?.toString()
            ?: return failure(TaskError.InvalidFileType)

        val extension = safeName.substringAfterLast('.', "").lowercase()
        if (extension !in ALLOWED_EXTENSIONS) return failure(TaskError.InvalidFileType)

        if (file.size > maxScriptSizeMb * 1024 * 1024) return failure(TaskError.FileTooLarge)

        val dir = Paths.get(scriptsBaseDir, taskId.toString())

        /* Commit the DB record first. If the file write fails afterward the DB row is inconsistent
           but there are no orphaned files. The reverse order (file first, then DB) would leave
           a file on disk with no DB record if the transaction rolls back. */
        val updatedConfig = task.config.toMutableMap()
        updatedConfig["fileName"] = safeName
        updatedConfig["directory"] = dir.toAbsolutePath().toString()
        task.config = updatedConfig
        task.scriptFileName = safeName
        val saved = taskRepository.save(task)

        Files.createDirectories(dir)
        file.transferTo(dir.resolve(safeName))

        return success(ScriptInfoResponse(taskId = taskId, fileName = safeName,
            sizeBytes = file.size, uploadedAt = saved.lastUpdated))
    }

    /** Returns metadata for the script uploaded to a task without exposing file contents. */
    @Transactional(readOnly = true)
    fun getScriptInfo(taskId: UUID, authenticationName: String): Either<TaskError, ScriptInfoResponse> {
        val currentUser = findCurrentUser(authenticationName) ?: return failure(TaskError.UserNotFound)
        val task = taskRepository.findByIdOrNull(taskId) ?: return failure(TaskError.TaskNotFound)

        if (task.isPrivate && !isAdmin(currentUser) && task.createdBy?.id != currentUser.id) {
            return failure(TaskError.AccessDenied)
        }

        val fileName = task.scriptFileName ?: return failure(TaskError.ScriptNotFound)
        val file = Paths.get(scriptsBaseDir, taskId.toString(), fileName).toFile()

        /* scriptFileName in DB but no file on disk is a data integrity anomaly — treat as not found. */
        if (!file.exists()) return failure(TaskError.ScriptNotFound)

        return success(ScriptInfoResponse(taskId = taskId, fileName = fileName,
            sizeBytes = file.length(), uploadedAt = task.lastUpdated))
    }

    // Helpers

    private fun findAccessibleWorkflow(workflowId: UUID, user: User): org.workflow.entity.Workflow? {
        if (isAdmin(user)) return workflowRepository.findByIdOrNull(workflowId)
        val userId = user.id ?: return null
        return workflowRepository.findByIdAndOwnerId(workflowId, userId)
    }

    /** Admins can access any task by ID; other users only their own. */
    private fun findOwnedTask(taskId: UUID, user: User): Task? {
        if (isAdmin(user)) return taskRepository.findByIdOrNull(taskId)
        val userId = user.id ?: return null
        return taskRepository.findByIdAndOwnerId(taskId, userId)
    }

    private fun findCurrentUser(username: String) = helpers.findUser(username)
    private fun isAdmin(user: User) = helpers.isAdmin(user)

    private fun Task.toResponse(effectiveWorkflowId: UUID? = workflow?.id): TaskResponse =
        TaskResponse(
            id = id,
            name = name,
            type = type,
            config = config,
            workflowId = effectiveWorkflowId,
            isPrivate = isPrivate,
            scriptFileName = scriptFileName
        )
}
