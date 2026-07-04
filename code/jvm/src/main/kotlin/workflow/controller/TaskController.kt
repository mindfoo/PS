package org.workflow.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.workflow.dto.ScriptInfoResponse
import org.workflow.dto.TaskCreateRequest
import org.workflow.dto.TaskResponse
import org.workflow.dto.TaskUpdateRequest
import org.workflow.dto.ExecutionResponse
import org.workflow.service.ExecutionService
import org.workflow.service.utils.ExecutionError
import org.workflow.service.utils.TaskError
import org.workflow.service.TaskService
import org.workflow.utils.Failure
import org.workflow.utils.Problem
import org.workflow.utils.Success
import org.workflow.utils.Uris
import java.util.UUID

/** Exposes task CRUD endpoints scoped by workflow ownership and RBAC. */
@RestController
@Tag(name = "Tasks", description = "Task CRUD endpoints")
class TaskController(
    private val taskService: TaskService,
    private val executionService: ExecutionService
) {

    @GetMapping(Uris.Tasks.BASE)
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(summary = "List tasks", description = "If workflowId is provided returns ordered entries for that workflow; otherwise returns all tasks visible to the caller")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Task list returned",
            content = [Content(schema = Schema(implementation = TaskResponse::class))]),
        ApiResponse(responseCode = "403", description = "Private resource — access denied",
            content = [Content(mediaType = Problem.PROB_TYPE)]),
        ApiResponse(responseCode = "404", description = "Workflow not found",
            content = [Content(mediaType = Problem.PROB_TYPE)])
    )
    fun list(
        @RequestParam(required = false) workflowId: UUID?,
        authentication: Authentication
    ): ResponseEntity<Any> {
        val result = if (workflowId != null)
            taskService.listByWorkflow(workflowId, authentication.name)
        else
            taskService.listAll(authentication.name)
        return when (result) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> result.value.toResponse()
        }
    }

    @GetMapping(Uris.Tasks.BY_ID)
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(summary = "Get task by id")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Task found",
            content = [Content(schema = Schema(implementation = TaskResponse::class))]),
        ApiResponse(responseCode = "403", description = "Private resource — access denied",
            content = [Content(mediaType = Problem.PROB_TYPE)]),
        ApiResponse(responseCode = "404", description = "Task not found",
            content = [Content(mediaType = Problem.PROB_TYPE)])
    )
    fun getById(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = taskService.getById(id, authentication.name)) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> result.value.toResponse()
        }

    @PostMapping(Uris.Tasks.BASE)
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(summary = "Create task")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Task created",
            content = [Content(schema = Schema(implementation = TaskResponse::class))]),
        ApiResponse(responseCode = "403", description = "Insufficient permissions",
            content = [Content(mediaType = Problem.PROB_TYPE)]),
        ApiResponse(responseCode = "404", description = "Workflow or user not found",
            content = [Content(mediaType = Problem.PROB_TYPE)])
    )
    fun create(
        @Valid @RequestBody request: TaskCreateRequest,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = taskService.create(request, authentication.name)) {
            is Success -> {
                val builder = ResponseEntity.status(HttpStatus.CREATED)
                result.value.id?.let { builder.location(Uris.Tasks.byId(it)) }
                builder.body(result.value)
            }
            is Failure -> result.value.toResponse()
        }

    @PutMapping(Uris.Tasks.BY_ID)
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(summary = "Update task")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Task updated",
            content = [Content(schema = Schema(implementation = TaskResponse::class))]),
        ApiResponse(responseCode = "403", description = "Private resource — access denied",
            content = [Content(mediaType = Problem.PROB_TYPE)]),
        ApiResponse(responseCode = "404", description = "Task or user not found",
            content = [Content(mediaType = Problem.PROB_TYPE)])
    )
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: TaskUpdateRequest,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = taskService.update(id, request, authentication.name)) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> result.value.toResponse()
        }

    @DeleteMapping(Uris.Tasks.BY_ID)
    @PreAuthorize("hasAuthority('task:delete')")
    @Operation(summary = "Delete task")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Task deleted"),
        ApiResponse(responseCode = "403", description = "Private resource — access denied",
            content = [Content(mediaType = Problem.PROB_TYPE)]),
        ApiResponse(responseCode = "404", description = "Task or user not found",
            content = [Content(mediaType = Problem.PROB_TYPE)])
    )
    fun delete(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = taskService.delete(id, authentication.name)) {
            is Success -> ResponseEntity.noContent().build()
            is Failure -> result.value.toResponse()
        }

    @PostMapping(Uris.Tasks.RUN)
    @PreAuthorize("hasAuthority('workflow:execute')")
    @Operation(summary = "Run a single task now", description = "Trigger a manual execution for a single task")
    @ApiResponses(
        ApiResponse(responseCode = "202", description = "Task execution started",
            content = [Content(schema = Schema(implementation = ExecutionResponse::class))]),
        ApiResponse(responseCode = "403", description = "Insufficient permissions",
            content = [Content(mediaType = Problem.PROB_TYPE)]),
        ApiResponse(responseCode = "404", description = "Task not found",
            content = [Content(mediaType = Problem.PROB_TYPE)])
    )
    fun runTask(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = executionService.triggerManualTask(id, authentication.name)) {
            is Success -> ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ExecutionResponse(executionId = result.value.toString(), status = "STARTED"))
            is Failure -> when (result.value) {
                ExecutionError.UserNotFound     -> Problem.response(404, Problem.userNotFound)
                ExecutionError.TaskNotFound     -> Problem.response(404, Problem.taskNotFound)
                ExecutionError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                ExecutionError.NotCancelable    -> Problem.response(409, Problem.notCancelable)
            }
        }

    @PostMapping(Uris.Tasks.SCRIPT, consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("hasAuthority('task:upload')")
    @Operation(
        summary = "Upload script for a task",
        description = "Uploads a script file (.py, .js, .ts, .sh, .bash) for a SCRIPT-type task. " +
                "Restricted to ADMIN and DEV roles. Updates the task config so the executor picks up the file automatically."
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Script uploaded successfully",
            content = [Content(schema = Schema(implementation = ScriptInfoResponse::class))]),
        ApiResponse(responseCode = "400", description = "Unsupported file type",
            content = [Content(mediaType = Problem.PROB_TYPE)]),
        ApiResponse(responseCode = "403", description = "Insufficient permissions or private task",
            content = [Content(mediaType = Problem.PROB_TYPE)]),
        ApiResponse(responseCode = "404", description = "Task or user not found",
            content = [Content(mediaType = Problem.PROB_TYPE)]),
        ApiResponse(responseCode = "413", description = "File exceeds the maximum allowed size",
            content = [Content(mediaType = Problem.PROB_TYPE)])
    )
    fun uploadScript(
        @PathVariable id: UUID,
        @RequestParam("file") file: MultipartFile,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = taskService.uploadScript(id, file, authentication.name)) {
            is Success -> ResponseEntity.status(HttpStatus.CREATED).body(result.value)
            is Failure -> result.value.toResponse()
        }

    @GetMapping(Uris.Tasks.SCRIPT_INFO)
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(
        summary = "Get script metadata for a task",
        description = "Returns the filename, size and upload timestamp for a task's uploaded script. " +
                "Accessible to any authenticated user with task:read (all roles)."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Script metadata returned",
            content = [Content(schema = Schema(implementation = ScriptInfoResponse::class))]),
        ApiResponse(responseCode = "403", description = "Private task — access denied",
            content = [Content(mediaType = Problem.PROB_TYPE)]),
        ApiResponse(responseCode = "404", description = "Task or script not found",
            content = [Content(mediaType = Problem.PROB_TYPE)])
    )
    fun getScriptInfo(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = taskService.getScriptInfo(id, authentication.name)) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> result.value.toResponse()
        }

    private fun TaskError.toResponse(): ResponseEntity<Any> = when (this) {
        TaskError.UserNotFound     -> Problem.response(404, Problem.userNotFound)
        TaskError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
        TaskError.TaskNotFound     -> Problem.response(404, Problem.taskNotFound)
        TaskError.AlreadyLinked    -> Problem.response(409, Problem.taskAlreadyLinked)
        TaskError.NotLinked        -> Problem.response(404, Problem.taskNotLinked)
        TaskError.AccessDenied     -> Problem.response(403, Problem.accessDenied)
        TaskError.InvalidFileType  -> Problem.response(400, Problem.invalidFileType)
        TaskError.FileTooLarge     -> Problem.response(413, Problem.fileTooLarge)
        TaskError.ScriptNotFound   -> Problem.response(404, Problem.scriptNotFound)
    }
}
