package org.workflow.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.workflow.dto.LoginRequest
import org.workflow.dto.MeResponse
import org.workflow.dto.RegisterRequest
import org.workflow.service.AuthService
import org.workflow.service.utils.AuthError
import org.workflow.utils.Failure
import org.workflow.utils.Problem
import org.workflow.utils.Success
import org.workflow.utils.Uris
import org.workflow.security.pipeline.AuthenticationInterceptor.Companion.COOKIE_NAME
import org.workflow.security.pipeline.AuthenticationInterceptor.Companion.USERNAME_COOKIE_NAME

@RestController
@Tag(name = "Auth", description = "Registration, login, logout and profile endpoints")
/** Exposes cookie-based authentication endpoints. */
class AuthController(
    private val authService: AuthService
) {
    companion object {
        /**
         * Set to `true` in production (requires HTTPS).
         * The cookie is sent over plain HTTP during local development.
         * When running behind HTTPS (production/staging), change this to `true`.
         */
        private const val SECURE_COOKIES = false
    }

    @PostMapping(Uris.Auth.REGISTER)
    @Operation(summary = "Register user", description = "Create a new user account")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "User registered successfully",
            content = [Content(schema = Schema(implementation = MeResponse::class))]),
        ApiResponse(responseCode = "409", description = "Username already taken",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "400", description = "Role not found or invalid input",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<Any> =
        when (val result = authService.register(request)) {
            is Success -> ResponseEntity.status(HttpStatus.CREATED).body(result.value)
            is Failure -> when (result.value) {
                AuthError.UsernameAlreadyTaken -> Problem.response(409, Problem.usernameAlreadyTaken)
                AuthError.RoleNotFound -> Problem.response(400, Problem.roleNotFound)
                AuthError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                AuthError.InvalidCredentials -> Problem.response(401, Problem.invalidCredentials)
            }
        }

    @PostMapping(Uris.Auth.LOGIN)
    @Operation(summary = "Login", description = "Authenticate and receive an HttpOnly token cookie")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Authentication successful — cookie set"),
        ApiResponse(responseCode = "401", description = "Invalid credentials",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<Any> =
        when (val result = authService.login(request)) {
            is Success -> {
                val tokenCookie = ResponseCookie.from(COOKIE_NAME, result.value.accessToken)
                    .httpOnly(true)          // not accessible via JS — protects against XSS
                    .secure(SECURE_COOKIES)  // false = HTTP (dev); true = HTTPS only (prod)
                    .sameSite("Strict")      // not sent on cross-site requests — protects against CSRF
                    .maxAge(AuthService.TOKEN_TTL_HOURS * 3600)
                    .path("/")
                    .build()

                val usernameCookie = ResponseCookie.from(USERNAME_COOKIE_NAME, request.username)
                    .httpOnly(false)         // readable by JS for display purposes (no sensitive data)
                    .secure(SECURE_COOKIES)
                    .sameSite("Strict")
                    .maxAge(AuthService.TOKEN_TTL_HOURS * 3600)
                    .path("/")
                    .build()

                ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, tokenCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, usernameCookie.toString())
                    .body(mapOf("message" to "Logged in successfully"))
            }
            is Failure -> when (result.value) {
                AuthError.InvalidCredentials -> Problem.response(401, Problem.invalidCredentials)
                AuthError.UsernameAlreadyTaken -> Problem.response(409, Problem.usernameAlreadyTaken)
                AuthError.RoleNotFound -> Problem.response(400, Problem.roleNotFound)
                AuthError.UserNotFound -> Problem.response(404, Problem.userNotFound)
            }
        }

    @PostMapping(Uris.Auth.LOGOUT)
    @Operation(summary = "Logout", description = "Revoke the current session token and clear cookies")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Logged out — cookies cleared"),
        ApiResponse(responseCode = "401", description = "Not authenticated",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun logout(request: HttpServletRequest): ResponseEntity<Void> {
        val tokenValue = request.cookies?.find { it.name == COOKIE_NAME }?.value
        if (!tokenValue.isNullOrBlank()) {
            authService.logout(tokenValue)
        }

        val expiredToken = ResponseCookie.from(COOKIE_NAME, "")
            .httpOnly(true)
            .secure(SECURE_COOKIES)
            .sameSite("Strict")
            .maxAge(0)
            .path("/")
            .build()

        val expiredUsername = ResponseCookie.from(USERNAME_COOKIE_NAME, "")
            .httpOnly(false)
            .secure(SECURE_COOKIES)
            .sameSite("Strict")
            .maxAge(0)
            .path("/")
            .build()

        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, expiredToken.toString())
            .header(HttpHeaders.SET_COOKIE, expiredUsername.toString())
            .build()
    }

    @GetMapping(Uris.Auth.ME)
    @Operation(summary = "Current profile", description = "Return authenticated user profile")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Profile retrieved",
            content = [Content(schema = Schema(implementation = MeResponse::class))]),
        ApiResponse(responseCode = "404", description = "User not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun me(authentication: Authentication): ResponseEntity<Any> =
        when (val result = authService.me(authentication.name)) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> when (result.value) {
                AuthError.UserNotFound -> Problem.response(404, Problem.userNotFound)
                AuthError.UsernameAlreadyTaken -> Problem.response(409, Problem.usernameAlreadyTaken)
                AuthError.RoleNotFound -> Problem.response(400, Problem.roleNotFound)
                AuthError.InvalidCredentials -> Problem.response(401, Problem.invalidCredentials)
            }
        }
}
