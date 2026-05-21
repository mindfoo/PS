package org.workflow.dto

import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime
import java.util.UUID

/** Payload to create a standalone task or a task inside a workflow. */
data class TaskCreateRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val type: String,
    /** Optional — if provided, task is immediately linked to this workflow. */
    val workflowId: UUID? = null,
    val config: Map<String, Any> = emptyMap(),
    val isPrivate: Boolean = false
)

/** Payload to update mutable task fields. */
data class TaskUpdateRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val type: String,
    val config: Map<String, Any> = emptyMap(),
    val isPrivate: Boolean = false
)

/** Task representation returned by task endpoints. */
data class TaskResponse(
    val id: UUID?,
    val name: String,
    val type: String,
    val config: Map<String, Any>,
    val workflowId: UUID?,
    val isPrivate: Boolean = false
)

/**
 * Task entry enriched with workflow-scoped ordering metadata from WorkflowTaskOrder.
 * Returned by GET /tasks?workflowId=... so the UI can render order and parallel stages.
 */
data class WorkflowTaskEntry(
    val taskId: UUID?,
    val name: String,
    val type: String,
    val config: Map<String, Any>,
    val orderId: UUID?,       // WorkflowTaskOrder.id — used as key in PATCH reorder
    val taskOrder: Int,
    val retryPolicy: Int,
    val dependsOnTaskId: UUID?,
    val isPrivate: Boolean = false
)

/** One item in a reorder request — identifies a WorkflowTaskOrder row and its new stage. */
data class TaskOrderItem(
    val orderId: UUID,
    val taskOrder: Int
)

/** Payload to reorder tasks within a workflow. */
data class TaskReorderRequest(
    val items: List<TaskOrderItem>
)

/** Payload to update the retry policy for a task within a workflow. */
data class RetryPolicyUpdateRequest(
    val retryPolicy: Int
)

/** Response returned when a task or workflow execution is triggered. */
data class ExecutionResponse(
    val executionId: String,
    val status: String
)

/** Per-task execution entry nested inside a workflow execution summary. */
data class TaskExecutionSummary(
    val executionId: UUID?,
    val taskId: UUID?,
    val taskName: String?,
    val status: String,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime?,
    val output: Map<String, Any>?
)

/** Summary of a single execution, returned in execution history. */
data class ExecutionSummaryResponse(
    val id: UUID?,
    val triggeredType: String,
    val type: String,
    val status: String,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime?,
    val triggeredBy: String,
    val retryCount: Int,
    val output: Map<String, Any>?,
    val taskExecutions: List<TaskExecutionSummary>? = null
)


