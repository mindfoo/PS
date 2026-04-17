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
import org.springframework.web.bind.annotation.RestController
import org.workflow.dto.ScheduleCreateRequest
import org.workflow.dto.ScheduleResponse
import org.workflow.dto.ScheduleUpdateRequest
import org.workflow.service.utils.ScheduleError
import org.workflow.service.ScheduleService
import org.workflow.utils.Failure
import org.workflow.utils.Problem
import org.workflow.utils.Success
import org.workflow.utils.Uris
import java.util.UUID

@RestController
@Tag(name = "Schedules", description = "Schedule creation and update for workflow executions")
/** Exposes schedule CRUD endpoints used to manage cron execution settings. */
class ScheduleController(
    private val scheduleService: ScheduleService
) {

    @GetMapping(Uris.Schedules.BASE)
    @PreAuthorize("hasAuthority('schedule:read')")
    @Operation(summary = "List schedules")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Schedule list returned",
            content = [Content(schema = Schema(implementation = ScheduleResponse::class))]),
        ApiResponse(responseCode = "404", description = "User not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun list(authentication: Authentication): ResponseEntity<Any> =
        when (val result = scheduleService.list(authentication.name)) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> when (result.value) {
                ScheduleError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                ScheduleError.ScheduleNotFound -> Problem.response(404, Problem.scheduleNotFound)
                ScheduleError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                ScheduleError.InvalidCronExpression -> Problem.response(400, Problem.invalidCronExpression)
            }
        }

    @GetMapping(Uris.Schedules.BY_ID)
    @PreAuthorize("hasAuthority('schedule:read')")
    @Operation(summary = "Get schedule by id")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Schedule found",
            content = [Content(schema = Schema(implementation = ScheduleResponse::class))]),
        ApiResponse(responseCode = "404", description = "Schedule not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun getById(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = scheduleService.getById(id, authentication.name)) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> when (result.value) {
                ScheduleError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                ScheduleError.ScheduleNotFound -> Problem.response(404, Problem.scheduleNotFound)
                ScheduleError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                ScheduleError.InvalidCronExpression -> Problem.response(400, Problem.invalidCronExpression)
            }
        }

    @PostMapping(Uris.Schedules.BASE)
    @PreAuthorize("hasAuthority('schedule:write')")
    @Operation(summary = "Create schedule")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Schedule created",
            content = [Content(schema = Schema(implementation = ScheduleResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid cron expression",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "404", description = "Workflow or user not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun create(
        @Valid @RequestBody request: ScheduleCreateRequest,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = scheduleService.create(request, authentication.name)) {
            is Success -> ResponseEntity.status(HttpStatus.CREATED).body(result.value)
            is Failure -> when (result.value) {
                ScheduleError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                ScheduleError.ScheduleNotFound -> Problem.response(404, Problem.scheduleNotFound)
                ScheduleError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                ScheduleError.InvalidCronExpression -> Problem.response(400, Problem.invalidCronExpression)
            }
        }

    @PutMapping(Uris.Schedules.BY_ID)
    @PreAuthorize("hasAuthority('schedule:write')")
    @Operation(summary = "Update schedule", description = "Change cron settings for a schedule")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Schedule updated",
            content = [Content(schema = Schema(implementation = ScheduleResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid cron expression",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "404", description = "Schedule not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ScheduleUpdateRequest,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = scheduleService.update(id, request, authentication.name)) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> when (result.value) {
                ScheduleError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                ScheduleError.ScheduleNotFound -> Problem.response(404, Problem.scheduleNotFound)
                ScheduleError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                ScheduleError.InvalidCronExpression -> Problem.response(400, Problem.invalidCronExpression)
            }
        }

    @DeleteMapping(Uris.Schedules.BY_ID)
    @PreAuthorize("hasAuthority('schedule:delete')")
    @Operation(summary = "Delete schedule")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Schedule deleted"),
        ApiResponse(responseCode = "404", description = "Schedule not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun delete(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Any> =
        when (val result = scheduleService.delete(id, authentication.name)) {
            is Success -> ResponseEntity.noContent().build()
            is Failure -> when (result.value) {
                ScheduleError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                ScheduleError.ScheduleNotFound -> Problem.response(404, Problem.scheduleNotFound)
                ScheduleError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
                ScheduleError.InvalidCronExpression -> Problem.response(400, Problem.invalidCronExpression)
            }
        }
}
