package org.workflow.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
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
import org.workflow.dto.TaskCreateRequest
import org.workflow.dto.TaskResponse
import org.workflow.dto.TaskUpdateRequest
import org.workflow.service.utils.TaskError
import org.workflow.service.TaskService
import org.workflow.utils.Failure
import org.workflow.utils.Problem
import org.workflow.utils.Success
import org.workflow.utils.Uris
import java.util.UUID

@RestController
@Tag(name = "Tasks", description = "Task CRUD endpoints")
/** Exposes task CRUD endpoints scoped by workflow ownership and RBAC. */
class TaskController(
    private val taskService: TaskService
) {

    @GetMapping(Uris.Tasks.BASE)
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(summary = "List tasks by workflow")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Task list returned",
            content = [Content(schema = Schema(implementation = TaskResponse::class))]),
        ApiResponse(responseCode = "404", description = "Workflow not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun listByWorkflow(
        @RequestParam workflowId: UUID,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = taskService.listByWorkflow(workflowId, authentication.name)) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> when (result.value) {
                TaskError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                TaskError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                TaskError.TaskNotFound -> Problem.response(404, Problem.taskNotFound)
            }
        }

    @GetMapping(Uris.Tasks.BY_ID)
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(summary = "Get task by id")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Task found",
            content = [Content(schema = Schema(implementation = TaskResponse::class))]),
        ApiResponse(responseCode = "404", description = "Task not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun getById(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = taskService.getById(id, authentication.name)) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> when (result.value) {
                TaskError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                TaskError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                TaskError.TaskNotFound -> Problem.response(404, Problem.taskNotFound)
            }
        }

    @PostMapping(Uris.Tasks.BASE)
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(summary = "Create task")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Task created",
            content = [Content(schema = Schema(implementation = TaskResponse::class))]),
        ApiResponse(responseCode = "404", description = "Workflow or user not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun create(
        @Valid @RequestBody request: TaskCreateRequest,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = taskService.create(request, authentication.name)) {
            is Success -> ResponseEntity.status(HttpStatus.CREATED).body(result.value)
            is Failure -> when (result.value) {
                TaskError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                TaskError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                TaskError.TaskNotFound -> Problem.response(404, Problem.taskNotFound)
            }
        }

    @PutMapping(Uris.Tasks.BY_ID)
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(summary = "Update task")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Task updated",
            content = [Content(schema = Schema(implementation = TaskResponse::class))]),
        ApiResponse(responseCode = "404", description = "Task or user not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: TaskUpdateRequest,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = taskService.update(id, request, authentication.name)) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> when (result.value) {
                TaskError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                TaskError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                TaskError.TaskNotFound -> Problem.response(404, Problem.taskNotFound)
            }
        }

    @DeleteMapping(Uris.Tasks.BY_ID)
    @PreAuthorize("hasAuthority('task:delete')")
    @Operation(summary = "Delete task")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Task deleted"),
        ApiResponse(responseCode = "404", description = "Task or user not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun delete(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = taskService.delete(id, authentication.name)) {
            is Success -> ResponseEntity.noContent().build()
            is Failure -> when (result.value) {
                TaskError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                TaskError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                TaskError.TaskNotFound -> Problem.response(404, Problem.taskNotFound)
            }
        }
}
