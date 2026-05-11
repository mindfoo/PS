package org.workflow.dto

import java.util.UUID

/** Admin-facing user payload used by management endpoints. */
data class UserAdminResponse(
    val id: UUID?,
    val username: String,
    val role: String,
    val permissions: List<String>
)

/** Request payload for changing a user's role. */
data class UserRoleUpdateRequest(
    val roleName: String
)

/** Role catalog entry with effective permission slugs. */
data class RoleSummaryResponse(
    val name: String,
    val permissions: List<String>
)