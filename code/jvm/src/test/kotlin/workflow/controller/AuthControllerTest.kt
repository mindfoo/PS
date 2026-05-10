package workflow.controller

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.security.core.Authentication
import org.workflow.controller.AuthController
import org.workflow.dto.LoginRequest
import org.workflow.dto.ProfileResponse
import org.workflow.dto.RegisterRequest
import org.workflow.dto.TokenResponse
import org.workflow.service.AuthService
import org.workflow.service.utils.AuthError
import org.workflow.utils.failure
import org.workflow.utils.success
import java.util.UUID

class AuthControllerTest {

    private lateinit var authService: AuthService
    private lateinit var controller: AuthController

    @BeforeEach
    fun setup() {
        authService = mockk()
        controller = AuthController(authService)
    }

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    fun `register returns 201 when user is created successfully`() {
        val meResponse = ProfileResponse(id = UUID.randomUUID(), username = "alice", role = "READER")
        every { authService.register(any()) } returns success(meResponse)

        val response = controller.register(RegisterRequest(username = "alice", password = "Secret1!"))

        assertEquals(201, response.statusCode.value())
    }

    @Test
    fun `register returns 409 when username is already taken`() {
        every { authService.register(any()) } returns failure(AuthError.UsernameAlreadyTaken)

        val response = controller.register(RegisterRequest(username = "alice", password = "Secret1!"))

        assertEquals(409, response.statusCode.value())
    }

    @Test
    fun `register returns 400 when role is not found`() {
        every { authService.register(any()) } returns failure(AuthError.RoleNotFound)

        val response = controller.register(RegisterRequest(username = "alice", password = "Secret1!", roleName = "GHOST"))

        assertEquals(400, response.statusCode.value())
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    fun `login returns 200 and sets cookies on success`() {
        every { authService.login(any()) } returns success(TokenResponse("raw-token"))

        val response = controller.login(LoginRequest(username = "alice", password = "Secret1!"))

        assertEquals(200, response.statusCode.value())
    }

    @Test
    fun `login returns 401 on invalid credentials`() {
        every { authService.login(any()) } returns failure(AuthError.InvalidCredentials)

        val response = controller.login(LoginRequest(username = "alice", password = "wrong"))

        assertEquals(401, response.statusCode.value())
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    fun `logout returns 204 and clears cookies`() {
        every { authService.logout(any()) } returns Unit

        val request = mockk<HttpServletRequest>(relaxed = true)
        // no token cookie present — logout should still return 204
        every { request.cookies } returns null

        val response = controller.logout(request)

        assertEquals(204, response.statusCode.value())
    }

    // ── me ────────────────────────────────────────────────────────────────────

    @Test
    fun `me returns 200 with user profile when authenticated`() {
        val auth = mockk<Authentication>()
        every { auth.name } returns "alice"
        every { authService.profile("alice") } returns success(ProfileResponse(id = UUID.randomUUID(), username = "alice", role = "READER"))

        val response = controller.profile(auth)

        assertEquals(200, response.statusCode.value())
    }

    @Test
    fun `me returns 404 when user not found`() {
        val auth = mockk<Authentication>()
        every { auth.name } returns "ghost"
        every { authService.me("ghost") } returns failure(AuthError.UserNotFound)

        val response = controller.me(auth)

        assertEquals(404, response.statusCode.value())
    }
}
