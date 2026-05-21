package org.workflow.dto

import jakarta.validation.constraints.NotBlank
import java.util.UUID
import jakarta.validation.constraints.NotEmpty

/** Payload to create a workflow. */
data class WorkflowCreateRequest(
    @field:NotBlank
    val name: String,
    val isPrivate: Boolean = false
)

/** Payload to update an existing workflow. */
data class WorkflowUpdateRequest(
    @field:NotBlank
    val name: String,
    val isPrivate: Boolean = false
)

/** Workflow representation returned by the API. */
data class WorkflowResponse(
    val id: UUID?,
    val name: String,
    val ownerId: UUID?,
    val ownerUsername: String,
    /** Status of the most recent top-level execution, or null if never run. */
    val lastRunStatus: String? = null,
    val isPrivate: Boolean = false
)

