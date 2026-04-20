package org.workflow.dto

import jakarta.validation.constraints.NotBlank
import java.util.UUID

/** Payload to create a workflow. */
data class WorkflowCreateRequest(
    @field:NotBlank
    val name: String
)

/** Payload to update an existing workflow. */
data class WorkflowUpdateRequest(
    @field:NotBlank
    val name: String
)

/** Workflow representation returned by the API. */
data class WorkflowResponse(
    val id: UUID?,
    val name: String,
    val ownerId: UUID?,
    val ownerUsername: String
)

