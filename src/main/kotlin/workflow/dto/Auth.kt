package org.workflow.dto

import jakarta.validation.constraints.NotBlank
import java.util.UUID

/** Request payload used to register a new user account. */
data class RegisterRequest(
    @field:NotBlank
    val username: String,
    @field:NotBlank
    val password: String,
    val roleName: String? = null
)

/** Request payload used to authenticate an existing user. */
data class LoginRequest(
    @field:NotBlank
    val username: String,
    @field:NotBlank
    val password: String
)

/** Internal carrier for the raw opaque token value returned by AuthService.login. The controller places this in an HttpOnly cookie — it is never sent directly to the client as a response body field in production use. */
data class TokenResponse(
    val accessToken: String
)

/** Authenticated user profile view exposed by the API. */
data class MeResponse(
    val id: UUID?,
    val username: String,
    val role: String
)

