package workflow.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.workflow.dto.UserRoleUpdateRequest
import org.workflow.entity.Roles
import org.workflow.entity.User
import org.workflow.repository.RoleRepository
import org.workflow.repository.UserRepository
import org.workflow.service.UserService
import org.workflow.service.utils.UserError
import org.workflow.utils.Failure
import org.workflow.utils.Success
import java.util.UUID

class UserServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var roleRepository: RoleRepository
    private lateinit var service: UserService

    private fun role(name: String = "READER") = Roles(id = UUID.randomUUID(), name = name)
    private fun user(role: Roles = role()) = User(
        id = UUID.randomUUID(), username = "alice",
        passwordValidation = "hashed", role = role
    )

    @BeforeEach
    fun setup() {
        userRepository  = mockk()
        roleRepository  = mockk()
        service = UserService(userRepository, roleRepository)
    }

    // ── listUsers ─────────────────────────────────────────────────────────────

    @Test
    fun `listUsers returns all users with permissions`() {
        val r = role(); r.permissions.clear()
        every { userRepository.findAllWithRoleAndPermissions() } returns listOf(user(r))

        val result = service.listUsers()

        assertEquals(1, result.size)
        assertEquals("alice", result[0].username)
    }

    // ── listRoles ─────────────────────────────────────────────────────────────

    @Test
    fun `listRoles returns all roles`() {
        every { roleRepository.findAllWithPermissions() } returns listOf(role("ADMIN"), role("READER"))

        val result = service.listRoles()

        assertEquals(2, result.size)
    }

    // ── updateUserRole ────────────────────────────────────────────────────────

    @Test
    fun `updateUserRole returns updated user on success`() {
        val userId    = UUID.randomUUID()
        val adminRole = role("ADMIN")
        val u         = user(role())
        every { userRepository.findByIdWithRoleAndPermissions(userId) } returns u
        every { roleRepository.findByNameWithPermissions("ADMIN") } returns adminRole
        every { userRepository.save(any()) } returns u.apply { role = adminRole }

        val result = service.updateUserRole(userId, UserRoleUpdateRequest("ADMIN"))

        assertTrue(result is Success)
        assertEquals("ADMIN", (result as Success).value.role)
    }

    @Test
    fun `updateUserRole returns UserNotFound when user missing`() {
        val userId = UUID.randomUUID()
        every { userRepository.findByIdWithRoleAndPermissions(userId) } returns null

        val result = service.updateUserRole(userId, UserRoleUpdateRequest("ADMIN"))

        assertTrue(result is Failure)
        assertEquals(UserError.UserNotFound, (result as Failure).value)
    }

    @Test
    fun `updateUserRole returns RoleNotFound when role missing`() {
        val userId = UUID.randomUUID()
        every { userRepository.findByIdWithRoleAndPermissions(userId) } returns user()
        every { roleRepository.findByNameWithPermissions("GHOST") } returns null

        val result = service.updateUserRole(userId, UserRoleUpdateRequest("GHOST"))

        assertTrue(result is Failure)
        assertEquals(UserError.RoleNotFound, (result as Failure).value)
    }
}
