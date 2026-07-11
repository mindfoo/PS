package org.workflow.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import java.util.UUID

/** Payload to create a new workflow schedule. */
data class ScheduleCreateRequest(
    @field:NotNull
    val workflowId: UUID,
    @field:NotBlank
    val cronExpression: String,
    @field:NotBlank
    val timezone: String = "UTC",
    val enabled: Boolean = true
)

/** Payload to change cron settings for an existing schedule. */
data class ScheduleUpdateRequest(
    @field:NotBlank
    val cronExpression: String,
    @field:NotBlank
    val timezone: String,
    val enabled: Boolean
)

/** Schedule details exposed by the API. */
data class ScheduleResponse(
    val id: UUID?,
    val workflowId: UUID?,
    val workflowName: String,
    val cronExpression: String,
    val timezone: String,
    val enabled: Boolean,
    val nextRunAt: LocalDateTime,
    val lastRunAt: LocalDateTime?
)

/** Response returned after a manual workflow trigger. */
data class TriggerWorkflowResponse(
    val executionId: UUID,
    val status: String
)

