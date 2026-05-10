package workflow.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.security.crypto.password.PasswordEncoder
import org.workflow.dto.LoginRequest
import org.workflow.dto.RegisterRequest
import org.workflow.entity.Roles
import org.workflow.entity.User
import org.workflow.entity.UserToken
import org.workflow.repository.RoleRepository
import org.workflow.repository.UserRepository
import org.workflow.repository.UserTokenRepository
import org.workflow.security.TokenUtils
import org.workflow.service.AuthService
import org.workflow.service.utils.AuthError
import org.workflow.utils.Failure
import org.workflow.utils.Success
import java.time.LocalDateTime
import java.util.UUID

class AuthServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var roleRepository: RoleRepository
    private lateinit var tokenRepository: UserTokenRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var service: AuthService

    private fun readerRole() = Roles(id = UUID.randomUUID(), name = "READER")
    private fun user(role: Roles = readerRole()) = User(
        id = UUID.randomUUID(), username = "alice",
        passwordValidation = "hashed", role = role
    )

    @BeforeEach
    fun setup() {
        userRepository  = mockk()
        roleRepository  = mockk()
        tokenRepository = mockk()
        passwordEncoder = mockk()
        service = AuthService(userRepository, roleRepository, tokenRepository, passwordEncoder)
    }

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    fun `register succeeds and returns ProfileResponse`() {
        val role = readerRole()
        val u    = user(role)
        every { userRepository.findByUsername("alice") } returns null
        every { roleRepository.findByName("READER") } returns role
        every { passwordEncoder.encode(any()) } returns "hashed"
        every { userRepository.save(any()) } returns u

        val result = service.register(RegisterRequest("alice", "Secret1!"))

        assertTrue(result is Success)
        assertEquals("alice", (result as Success).value.username)
    }

    @Test
    fun `register fails with UsernameAlreadyTaken when username exists`() {
        every { userRepository.findByUsername("alice") } returns user()

        val result = service.register(RegisterRequest("alice", "Secret1!"))

        assertTrue(result is Failure)
        assertEquals(AuthError.UsernameAlreadyTaken, (result as Failure).value)
    }

    @Test
    fun `register fails with RoleNotFound when role does not exist`() {
        every { userRepository.findByUsername("new") } returns null
        every { roleRepository.findByName("GHOST") } returns null

        val result = service.register(RegisterRequest("new", "Secret1!", roleName = "GHOST"))

        assertTrue(result is Failure)
        assertEquals(AuthError.RoleNotFound, (result as Failure).value)
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    fun `login succeeds and returns token`() {
        val u = user()
        every { userRepository.findByUsername("alice") } returns u
        every { passwordEncoder.matches("Secret1!", "hashed") } returns true
        every { tokenRepository.save(any()) } returnsArgument 0

        val result = service.login(LoginRequest("alice", "Secret1!"))

        assertTrue(result is Success)
        assertNotNull((result as Success).value.accessToken)
    }

    @Test
    fun `login fails with InvalidCredentials when user not found`() {
        every { userRepository.findByUsername("ghost") } returns null

        val result = service.login(LoginRequest("ghost", "anything"))

        assertTrue(result is Failure)
        assertEquals(AuthError.InvalidCredentials, (result as Failure).value)
    }

    @Test
    fun `login fails with InvalidCredentials when password is wrong`() {
        val u = user()
        every { userRepository.findByUsername("alice") } returns u
        every { passwordEncoder.matches("wrong", "hashed") } returns false

        val result = service.login(LoginRequest("alice", "wrong"))

        assertTrue(result is Failure)
        assertEquals(AuthError.InvalidCredentials, (result as Failure).value)
    }

    @Test
    fun `login fails with InvalidCredentials when username is blank`() {
        val result = service.login(LoginRequest("", "Secret1!"))

        assertTrue(result is Failure)
        assertEquals(AuthError.InvalidCredentials, (result as Failure).value)
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    fun `logout deletes token by hash`() {
        every { tokenRepository.deleteByTokenHash(any()) } returns Unit

        service.logout("raw-token")

        verify(exactly = 1) { tokenRepository.deleteByTokenHash(any()) }
    }

    // ── me ────────────────────────────────────────────────────────────────────

    @Test
    fun `me returns ProfileResponse for existing user`() {
        every { userRepository.findByUsername("alice") } returns user()

        val result = service.profile("alice")

        assertTrue(result is Success)
        assertEquals("alice", (result as Success).value.username)
    }

    @Test
    fun `me returns UserNotFound when user does not exist`() {
        every { userRepository.findByUsername("ghost") } returns null

        val result = service.me("ghost")

        assertTrue(result is Failure)
        assertEquals(AuthError.UserNotFound, (result as Failure).value)
    }
}
