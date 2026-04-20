package org.workflow.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

/** Payload to create a task inside a workflow. */
data class TaskCreateRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val type: String,
    @field:NotNull
    val workflowId: UUID,
    val config: Map<String, Any> = emptyMap()
)

/** Payload to update mutable task fields. */
data class TaskUpdateRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val type: String,
    val config: Map<String, Any> = emptyMap()
)

/** Task representation returned by task endpoints. */
data class TaskResponse(
    val id: UUID?,
    val name: String,
    val type: String,
    val config: Map<String, Any>,
    val workflowId: UUID?
)

