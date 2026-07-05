package workflow.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.workflow.dto.LoginRequest
import org.workflow.dto.RegisterRequest
import org.workflow.entity.Roles
import org.workflow.entity.User
import org.workflow.entity.enums.RoleType
import org.workflow.repository.RoleRepository
import org.workflow.repository.UserRepository
import org.workflow.repository.UserTokenRepository
import org.workflow.service.AuthService
import org.workflow.service.ServiceHelpers
import org.workflow.service.utils.AuthLoginError
import org.workflow.service.utils.AuthRegisterError
import org.workflow.utils.Failure
import org.workflow.utils.Success
import java.util.*

class AuthServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var roleRepository: RoleRepository
    private lateinit var tokenRepository: UserTokenRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var service: AuthService
    private lateinit var helpers: ServiceHelpers

    private fun readerRole() = Roles(id = UUID.randomUUID(), name = RoleType.READER)
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
        helpers = mockk()
        service = AuthService(userRepository, roleRepository, tokenRepository, passwordEncoder, helpers)
    }

    // register

    @Test
    fun `register succeeds and returns ProfileResponse`() {
        val role = readerRole()
        val u    = user(role)
        every { helpers.findUser("alice") } returns null
        every { roleRepository.findByName(RoleType.READER) } returns role
        every { passwordEncoder.encode(any()) } returns "hashed"
        every { userRepository.save(any()) } returns u

        val result = service.register(RegisterRequest("alice", "Secret1!Aa"))

        assertTrue(result is Success)
        assertEquals("alice", (result as Success).value.username)
    }

    @Test
    fun `register fails with UsernameAlreadyTaken when username exists`() {
        every { helpers.findUser("alice") } returns user()

        val result = service.register(RegisterRequest("alice", "Secret1!Aa"))

        assertTrue(result is Failure)
        assertEquals(AuthRegisterError.UsernameAlreadyTaken, (result as Failure).value)
    }

    @Test
    fun `register fails with RoleNotFound when role does not exist`() {
        every { helpers.findUser("new") } returns null

        val result = service.register(RegisterRequest("new", "Secret1!Aa", roleName = "GHOST"))

        assertTrue(result is Failure)
        assertEquals(AuthRegisterError.RoleNotFound, (result as Failure).value)
    }

    @Test
    fun `register fails with InsecurePassword when password is weak`() {
        val result = service.register(RegisterRequest("alice", "weakpass"))

        assertTrue(result is Failure)
        assertEquals(AuthRegisterError.InsecurePassword, (result as Failure).value)
    }

    // login

    @Test
    fun `login succeeds and returns token`() {
        val u = user()
        every { helpers.findUser("alice") } returns u
        every { passwordEncoder.matches("Secret1!Aa", "hashed") } returns true
        every { tokenRepository.save(any()) } returnsArgument 0

        val result = service.login(LoginRequest("alice", "Secret1!Aa"))

        assertTrue(result is Success)
        assertNotNull((result as Success).value.accessToken)
    }

    @Test
    fun `login fails with InvalidCredentials when user not found`() {
        every { helpers.findUser("ghost") } returns null

        val result = service.login(LoginRequest("ghost", "anything"))

        assertTrue(result is Failure)
        assertEquals(AuthLoginError.InvalidCredentials, (result as Failure).value)
    }

    @Test
    fun `login fails with InvalidCredentials when password is wrong`() {
        val u = user()
        every { helpers.findUser("alice") } returns u
        every { passwordEncoder.matches("wrong", "hashed") } returns false

        val result = service.login(LoginRequest("alice", "wrong"))

        assertTrue(result is Failure)
        assertEquals(AuthLoginError.InvalidCredentials, (result as Failure).value)
    }

    @Test
    fun `login fails with InvalidCredentials when username is blank`() {
        val result = service.login(LoginRequest("", "Secret1!Aa"))

        assertTrue(result is Failure)
        assertEquals(AuthLoginError.InvalidCredentials, (result as Failure).value)
    }

    // logout

    @Test
    fun `logout deletes token by hash`() {
        every { tokenRepository.deleteByTokenHash(any()) } returns Unit

        service.logout("raw-token")

        verify(exactly = 1) { tokenRepository.deleteByTokenHash(any()) }
    }

    // me

    @Test
    fun `me returns ProfileResponse for existing user`() {
        every { helpers.findUser("alice") } returns user()

        val result = service.profile("alice")

        assertTrue(result is Success)
        assertEquals("alice", (result as Success).value.username)
    }

    @Test
    fun `me returns UserNotFound when user does not exist`() {
        every { helpers.findUser("ghost") } returns null

        val result = service.profile("ghost")

        assertTrue(result is Failure)
        assertEquals(AuthLoginError.UserNotFound, (result as Failure<AuthLoginError>).value)
    }
}
