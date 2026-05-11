package org.workflow.dto

import java.util.UUID

/** Legacy request payload used by the user registration endpoint. */
data class UserCreateRequest(
    val username: String,
    val passwordPlain: String,
    val roleName: String
)

/** User data returned by legacy user endpoints. */
data class UserResponse(
    val id: UUID?,
    val username: String,
    val role: String
)

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