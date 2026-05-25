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
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.workflow.dto.ExecutionSummaryResponse
import org.workflow.dto.RetryPolicyUpdateRequest
import org.workflow.dto.TaskReorderRequest
import org.workflow.dto.TriggerWorkflowResponse
import org.workflow.dto.WorkflowCreateRequest
import org.workflow.dto.WorkflowResponse
import org.workflow.dto.WorkflowUpdateRequest
import org.workflow.service.ExecutionService
import org.workflow.service.TaskService
import org.workflow.service.utils.TaskError
import org.workflow.service.utils.WorkflowError
import org.workflow.service.WorkflowService
import org.workflow.utils.Failure
import org.workflow.utils.Problem
import org.workflow.utils.Success
import org.workflow.utils.Uris
import java.util.UUID

/** Handles workflow CRUD operations, manual execution triggers, and task linking. */
@RestController
@Tag(name = "Workflows", description = "Workflow CRUD and manual run")
class WorkflowController(
    private val workflowService: WorkflowService,
    private val executionService: ExecutionService,
    private val taskService: TaskService
) {

    @GetMapping(Uris.Workflows.BASE)
    @PreAuthorize("hasAuthority('workflow:read')")
    @Operation(summary = "List workflows", description = "Return workflows visible to the authenticated user")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "List returned",
            content = [Content(schema = Schema(implementation = WorkflowResponse::class))]),
        ApiResponse(responseCode = "401", description = "Not authenticated",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "403", description = "Private resource — access denied",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "404", description = "User not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun list(authentication: Authentication): ResponseEntity<Any> =
        when (val result = workflowService.list(authentication.name)) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> when (result.value) {
                WorkflowError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                WorkflowError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                WorkflowError.AccessDenied -> Problem.response(403, Problem.accessDenied)
            }
        }

    @GetMapping(Uris.Workflows.BY_ID)
    @PreAuthorize("hasAuthority('workflow:read')")
    @Operation(summary = "Get workflow by id")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Workflow found",
            content = [Content(schema = Schema(implementation = WorkflowResponse::class))]),
        ApiResponse(responseCode = "403", description = "Private resource — access denied",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "404", description = "Workflow not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun getById(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = workflowService.getById(id, authentication.name)) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> when (result.value) {
                WorkflowError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                WorkflowError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                WorkflowError.AccessDenied -> Problem.response(403, Problem.accessDenied)
            }
        }

    @PostMapping(Uris.Workflows.BASE)
    @PreAuthorize("hasAuthority('workflow:write')")
    @Operation(summary = "Create workflow")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Workflow created",
            content = [Content(schema = Schema(implementation = WorkflowResponse::class))]),
        ApiResponse(responseCode = "403", description = "Insufficient permissions",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "404", description = "User not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun create(
        @Valid @RequestBody request: WorkflowCreateRequest,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = workflowService.create(request, authentication.name)) {
            is Success -> ResponseEntity.status(HttpStatus.CREATED).body(result.value)
            is Failure -> when (result.value) {
                WorkflowError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                WorkflowError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                WorkflowError.AccessDenied -> Problem.response(403, Problem.accessDenied)
            }
        }

    @PutMapping(Uris.Workflows.BY_ID)
    @PreAuthorize("hasAuthority('workflow:write')")
    @Operation(summary = "Update workflow")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Workflow updated",
            content = [Content(schema = Schema(implementation = WorkflowResponse::class))]),
        ApiResponse(responseCode = "403", description = "Private resource — access denied",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "404", description = "Workflow or user not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: WorkflowUpdateRequest,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = workflowService.update(id, request, authentication.name)) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> when (result.value) {
                WorkflowError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                WorkflowError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                WorkflowError.AccessDenied -> Problem.response(403, Problem.accessDenied)
            }
        }

    @DeleteMapping(Uris.Workflows.BY_ID)
    @PreAuthorize("hasAuthority('workflow:delete')")
    @Operation(summary = "Delete workflow")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Workflow deleted"),
        ApiResponse(responseCode = "403", description = "Private resource — access denied",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "404", description = "Workflow or user not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun delete(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = workflowService.delete(id, authentication.name)) {
            is Success -> ResponseEntity.noContent().build()
            is Failure -> when (result.value) {
                WorkflowError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                WorkflowError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                WorkflowError.AccessDenied -> Problem.response(403, Problem.accessDenied)
            }
        }

    @PostMapping(Uris.Workflows.RUN)
    @PreAuthorize("hasAuthority('workflow:execute')")
    @Operation(summary = "Run workflow now", description = "Trigger manual execution for a workflow")
    @ApiResponses(
        ApiResponse(responseCode = "202", description = "Execution started",
            content = [Content(schema = Schema(implementation = TriggerWorkflowResponse::class))]),
        ApiResponse(responseCode = "403", description = "Insufficient permissions",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "404", description = "Workflow or user not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun runNow(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<TriggerWorkflowResponse> {
        val executionId = executionService.triggerManualWorkflow(id, authentication.name)
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(TriggerWorkflowResponse(executionId = executionId, status = "STARTED"))
    }

    @PatchMapping(Uris.Workflows.TASK_ORDER)
    @PreAuthorize("hasAuthority('workflow:write')")
    @Operation(summary = "Reorder tasks", description = "Update taskOrder for each WorkflowTaskOrder row. Tasks sharing the same taskOrder run in parallel.")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Order updated"),
        ApiResponse(responseCode = "403", description = "Private resource — access denied",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "404", description = "Workflow or order row not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun reorderTasks(
        @PathVariable id: UUID,
        @RequestBody request: TaskReorderRequest,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = workflowService.reorderTasks(id, request, authentication.name)) {
            is Success -> ResponseEntity.noContent().build()
            is Failure -> when (result.value) {
                WorkflowError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                WorkflowError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                WorkflowError.AccessDenied -> Problem.response(403, Problem.accessDenied)
            }
        }

    @GetMapping(Uris.Workflows.EXECUTIONS)
    @PreAuthorize("hasAuthority('workflow:read')")
    @Operation(summary = "List executions", description = "Return execution history for a workflow, most recent first")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Execution list returned",
            content = [Content(schema = Schema(implementation = ExecutionSummaryResponse::class))]),
        ApiResponse(responseCode = "403", description = "Private resource — access denied",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "404", description = "Workflow not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun listExecutions(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = workflowService.listExecutions(id, authentication.name)) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> when (result.value) {
                WorkflowError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                WorkflowError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                WorkflowError.AccessDenied -> Problem.response(403, Problem.accessDenied)
            }
        }

    @PostMapping(Uris.Workflows.LINK_TASK)
    @PreAuthorize("hasAuthority('workflow:write')")
    @Operation(summary = "Link existing task to workflow")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Task linked"),
        ApiResponse(responseCode = "404", description = "Workflow or task not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "403", description = "Private resource — access denied",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "409", description = "Task already linked",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun linkTask(
        @PathVariable id: UUID,
        @PathVariable taskId: UUID,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = taskService.linkToWorkflow(taskId, id, authentication.name)) {
            is Success -> ResponseEntity.noContent().build()
            is Failure -> when (result.value) {
                TaskError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                TaskError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                TaskError.TaskNotFound -> Problem.response(404, Problem.taskNotFound)
                TaskError.AlreadyLinked -> Problem.response(409, Problem.taskAlreadyLinked)
                TaskError.NotLinked -> Problem.response(404, Problem.taskNotLinked)
                TaskError.AccessDenied -> Problem.response(403, Problem.accessDenied)
            }
        }

    @PatchMapping(Uris.Workflows.TASK_RETRY_POLICY)
    @PreAuthorize("hasAuthority('workflow:write')")
    @Operation(summary = "Update task retry policy", description = "Set the number of retry attempts for a task in a workflow")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Retry policy updated"),
        ApiResponse(responseCode = "403", description = "Private resource — access denied",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "404", description = "Workflow or task not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun updateRetryPolicy(
        @PathVariable id: UUID,
        @PathVariable taskId: UUID,
        @RequestBody request: RetryPolicyUpdateRequest,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = workflowService.updateRetryPolicy(id, taskId, request, authentication.name)) {
            is Success -> ResponseEntity.noContent().build()
            is Failure -> when (result.value) {
                WorkflowError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                WorkflowError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                WorkflowError.AccessDenied -> Problem.response(403, Problem.accessDenied)
            }
        }

    @DeleteMapping(Uris.Workflows.LINK_TASK)
    @PreAuthorize("hasAuthority('workflow:write')")
    @Operation(summary = "Unlink task from workflow")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Task unlinked"),
        ApiResponse(responseCode = "403", description = "Private resource — access denied",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "404", description = "Workflow, task, or link not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun unlinkTask(
        @PathVariable id: UUID,
        @PathVariable taskId: UUID,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = taskService.unlinkFromWorkflow(taskId, id, authentication.name)) {
            is Success -> ResponseEntity.noContent().build()
            is Failure -> when (result.value) {
                TaskError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                TaskError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                TaskError.TaskNotFound -> Problem.response(404, Problem.taskNotFound)
                TaskError.AlreadyLinked -> Problem.response(409, Problem.taskAlreadyLinked)
                TaskError.NotLinked -> Problem.response(404, Problem.taskNotLinked)
                TaskError.AccessDenied -> Problem.response(403, Problem.accessDenied)
            }
        }

    @GetMapping(Uris.Executions.BY_ID)
    @PreAuthorize("hasAuthority('workflow:read')")
    @Operation(summary = "Get execution by id", description = "Returns a single execution with per-task child entries (for polling)")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Execution found",
            content = [Content(schema = Schema(implementation = ExecutionSummaryResponse::class))]),
        ApiResponse(responseCode = "403", description = "Private resource — access denied",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "404", description = "Execution not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun getExecution(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = workflowService.getExecution(id, authentication.name)) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> when (result.value) {
                WorkflowError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                WorkflowError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                WorkflowError.AccessDenied -> Problem.response(403, Problem.accessDenied)
            }
        }

    @PostMapping(Uris.Executions.CANCEL)
    @PreAuthorize("hasAuthority('workflow:execute')")
    @Operation(summary = "Cancel execution", description = "Stops a PENDING or RUNNING execution and marks it as CANCELED")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Execution canceled"),
        ApiResponse(responseCode = "409", description = "Execution is not cancelable",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "404", description = "Execution not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun cancelExecution(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Any> {
        val canceled = executionService.cancelExecution(id, authentication.name)
        return if (canceled) ResponseEntity.noContent().build()
        else Problem.response(409, Problem.notCancelable)
    }
}
