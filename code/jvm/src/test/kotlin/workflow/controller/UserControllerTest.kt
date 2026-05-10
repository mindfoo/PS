package workflow.controller

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.workflow.controller.UserController
import org.workflow.dto.UserAdminResponse
import org.workflow.dto.UserCreateRequest
import org.workflow.dto.UserRoleUpdateRequest
import org.workflow.dto.RoleSummaryResponse
import org.workflow.service.UserService
import org.workflow.service.utils.UserError
import org.workflow.utils.failure
import org.workflow.utils.success
import java.util.UUID

class UserControllerTest {

    private lateinit var userService: UserService
    private lateinit var controller: UserController

    @BeforeEach
    fun setup() {
        userService = mockk()
        controller = UserController(userService)
    }

    // ── listUsers ─────────────────────────────────────────────────────────────

    @Test
    fun `listUsers returns 200 with all users`() {
        val users = listOf(
            UserAdminResponse(UUID.randomUUID(), "alice", "ADMIN", listOf("workflow:read")),
            UserAdminResponse(UUID.randomUUID(), "bob",   "READER", listOf("workflow:read"))
        )
        every { userService.listUsers() } returns users

        val response = controller.listUsers()

        assertEquals(200, response.statusCode.value())
        assertEquals(2, response.body!!.size)
    }

    @Test
    fun `listUsers returns 200 with empty list when no users exist`() {
        every { userService.listUsers() } returns emptyList()

        val response = controller.listUsers()

        assertEquals(200, response.statusCode.value())
        assertEquals(0, response.body!!.size)
    }

    // ── listRoles ─────────────────────────────────────────────────────────────

    @Test
    fun `listRoles returns 200 with role catalog`() {
        val roles = listOf(
            RoleSummaryResponse("ADMIN", listOf("workflow:read", "workflow:write")),
            RoleSummaryResponse("READER", listOf("workflow:read"))
        )
        every { userService.listRoles() } returns roles

        val response = controller.listRoles()

        assertEquals(200, response.statusCode.value())
        assertEquals(2, response.body!!.size)
    }

    // ── updateUserRole ────────────────────────────────────────────────────────

    @Test
    fun `updateUserRole returns 200 on success`() {
        val userId = UUID.randomUUID()
        val updated = UserAdminResponse(userId, "alice", "WRITER", listOf("workflow:read", "workflow:write"))
        every { userService.updateUserRole(userId, any()) } returns success(updated)

        val response = controller.updateUserRole(userId, UserRoleUpdateRequest("WRITER"))

        assertEquals(200, response.statusCode.value())
    }

    @Test
    fun `updateUserRole returns 404 when user not found`() {
        val userId = UUID.randomUUID()
        every { userService.updateUserRole(userId, any()) } returns failure(UserError.UserNotFound)

        val response = controller.updateUserRole(userId, UserRoleUpdateRequest("ADMIN"))

        assertEquals(404, response.statusCode.value())
    }

    @Test
    fun `updateUserRole returns 400 when role not found`() {
        val userId = UUID.randomUUID()
        every { userService.updateUserRole(userId, any()) } returns failure(UserError.RoleNotFound)

        val response = controller.updateUserRole(userId, UserRoleUpdateRequest("GHOST"))

        assertEquals(400, response.statusCode.value())
    }

    // ── registerUser (legacy) ─────────────────────────────────────────────────

    @Test
    fun `registerUser returns 201 when created`() {
        every { userService.createUser(any()) } returns success(
            org.workflow.dto.UserResponse(UUID.randomUUID(), "charlie", "READER")
        )

        val response = controller.registerUser(UserCreateRequest("charlie", "Secret1!", "READER"))

        assertEquals(201, response.statusCode.value())
    }

    @Test
    fun `registerUser returns 409 when username taken`() {
        every { userService.createUser(any()) } returns failure(UserError.UsernameAlreadyTaken)

        val response = controller.registerUser(UserCreateRequest("alice", "Secret1!", "READER"))

        assertEquals(409, response.statusCode.value())
    }
}
