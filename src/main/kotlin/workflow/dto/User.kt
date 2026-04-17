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
    val name: String,
    val role: String
)