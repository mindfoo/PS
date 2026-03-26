package org.workflow.dto

import org.workflow.entity.Role
import java.util.UUID

data class UserCreateRequest(
    val username: String,
    val passwordPlain: String,
    val roleName: String
)

data class UserResponse(
    val id: UUID?,
    val name: String,
    val roles: List<String>
)