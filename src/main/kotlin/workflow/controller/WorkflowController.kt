package org.workflow.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
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
import org.springframework.web.bind.annotation.RestController
import org.workflow.dto.TriggerWorkflowResponse
import org.workflow.dto.WorkflowCreateRequest
import org.workflow.dto.WorkflowResponse
import org.workflow.dto.WorkflowUpdateRequest
import org.workflow.service.ExecutionService
import org.workflow.service.utils.WorkflowError
import org.workflow.service.WorkflowService
import org.workflow.utils.Failure
import org.workflow.utils.Problem
import org.workflow.utils.Success
import org.workflow.utils.Uris
import java.util.UUID

@RestController
@Tag(name = "Workflows", description = "Workflow CRUD and manual run")
@SecurityRequirement(name = "bearerAuth")
/** Handles workflow CRUD operations and manual execution triggers. */
class WorkflowController(
    private val workflowService: WorkflowService,
    private val executionService: ExecutionService
) {

    @GetMapping(Uris.Workflows.BASE)
    @PreAuthorize("hasAuthority('workflow:read')")
    @Operation(summary = "List workflows", description = "Return workflows visible to the authenticated user")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "List returned",
            content = [Content(schema = Schema(implementation = WorkflowResponse::class))]),
        ApiResponse(responseCode = "401", description = "Not authenticated",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun list(authentication: Authentication): ResponseEntity<Any> =
        when (val result = workflowService.list(authentication.name)) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> when (result.value) {
                WorkflowError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                WorkflowError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
            }
        }

    @GetMapping(Uris.Workflows.BY_ID)
    @PreAuthorize("hasAuthority('workflow:read')")
    @Operation(summary = "Get workflow by id")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Workflow found",
            content = [Content(schema = Schema(implementation = WorkflowResponse::class))]),
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
            }
        }

    @PostMapping(Uris.Workflows.BASE)
    @PreAuthorize("hasAuthority('workflow:write')")
    @Operation(summary = "Create workflow")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Workflow created",
            content = [Content(schema = Schema(implementation = WorkflowResponse::class))]),
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
            }
        }

    @PutMapping(Uris.Workflows.BY_ID)
    @PreAuthorize("hasAuthority('workflow:write')")
    @Operation(summary = "Update workflow")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Workflow updated",
            content = [Content(schema = Schema(implementation = WorkflowResponse::class))]),
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
            }
        }

    @DeleteMapping(Uris.Workflows.BY_ID)
    @PreAuthorize("hasAuthority('workflow:delete')")
    @Operation(summary = "Delete workflow")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Workflow deleted"),
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
            }
        }

    @PostMapping(Uris.Workflows.RUN)
    @PreAuthorize("hasAuthority('workflow:execute')")
    @Operation(summary = "Run workflow now", description = "Trigger manual execution for a workflow")
    @ApiResponses(
        ApiResponse(responseCode = "202", description = "Execution started",
            content = [Content(schema = Schema(implementation = TriggerWorkflowResponse::class))]),
        ApiResponse(responseCode = "404", description = "Workflow not found",
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
}
