package org.workflow.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import org.workflow.service.ExecutionEventService
import org.workflow.utils.Problem
import org.workflow.utils.Uris
import java.util.UUID

@RestController
@Tag(name = "Execution Events", description = "SSE stream for live execution status updates")
class ExecutionEventController(
    private val executionEventService: ExecutionEventService
) {

    @GetMapping(Uris.Executions.EVENTS, produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Subscribe to live execution events via SSE")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "SSE stream opened"),
        ApiResponse(responseCode = "400", description = "Invalid execution ID format")
    )
    fun subscribe(
        @PathVariable id: String,
        authentication: Authentication
    ): ResponseEntity<*> {
        val executionId = try {
            UUID.fromString(id)
        } catch (e: IllegalArgumentException) {
            return Problem.response(400, Problem.badRequest)
        }
        return ResponseEntity.ok(executionEventService.subscribe(executionId))
    }
}
