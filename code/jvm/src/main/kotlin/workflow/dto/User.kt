package org.workflow.dto

import org.workflow.entity.enums.RoleType
import java.util.UUID

/** Admin-facing user payload used by management endpoints. */
data class UserAdminResponse(
    val id: UUID?,
    val username: String,
    val role: RoleType,
    val permissions: List<String>
)

/** Request payload for changing a user's role. */
data class UserRoleUpdateRequest(
    val roleName: String
)

/** Role catalog entry with effective permission slugs. */
data class RoleSummaryResponse(
    val name: RoleType,
    val permissions: List<String>
)