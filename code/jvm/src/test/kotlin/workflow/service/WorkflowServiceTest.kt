package workflow.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.workflow.dto.WorkflowCreateRequest
import org.workflow.dto.WorkflowUpdateRequest
import org.workflow.entity.Roles
import org.workflow.entity.User
import org.workflow.entity.Workflow
import org.workflow.repository.AlertRepository
import org.workflow.repository.ExecutionLogRepository
import org.workflow.repository.ScheduleRepository
import org.workflow.repository.TaskRepository
import org.workflow.repository.UserRepository
import org.workflow.repository.WorkflowRepository
import org.workflow.repository.WorkflowTaskOrderRepository
import org.workflow.service.WorkflowService
import org.workflow.service.utils.WorkflowError
import org.workflow.utils.Failure
import org.workflow.utils.Success
import java.util.Optional
import java.util.UUID

class WorkflowServiceTest {

    private lateinit var workflowRepository: WorkflowRepository
    private lateinit var userRepository: UserRepository
    private lateinit var wtoRepository: WorkflowTaskOrderRepository
    private lateinit var executionLogRepository: ExecutionLogRepository
    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var taskRepository: TaskRepository
    private lateinit var alertRepository: AlertRepository
    private lateinit var service: WorkflowService

    private fun readerRole() = Roles(id = UUID.randomUUID(), name = "READER")
    private fun adminRole()  = Roles(id = UUID.randomUUID(), name = "ADMIN")
    private fun user(role: Roles = readerRole(), name: String = "alice") =
        User(id = UUID.randomUUID(), username = name, passwordValidation = "h", role = role)
    private fun workflow(owner: User) =
        Workflow(id = UUID.randomUUID(), name = "My WF", created_by = owner)

    @BeforeEach
    fun setup() {
        workflowRepository    = mockk()
        userRepository        = mockk()
        wtoRepository         = mockk()
        executionLogRepository = mockk()
        scheduleRepository    = mockk()
        taskRepository        = mockk()
        alertRepository       = mockk()
        service = WorkflowService(
            workflowRepository, userRepository, wtoRepository,
            executionLogRepository, scheduleRepository, taskRepository, alertRepository
        )
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    fun `list returns all workflows for admin`() {
        val admin = user(adminRole(), "admin")
        val wf    = workflow(admin)
        every { userRepository.findByUsername("admin") } returns admin
        every { workflowRepository.findAll() } returns listOf(wf)
        every { executionLogRepository.findLatestByWorkflowId(any()) } returns null

        val result = service.list("admin")

        assertTrue(result is Success)
        assertEquals(1, (result as Success).value.size)
    }

    @Test
    fun `list returns only owned workflows for non-admin`() {
        val alice = user()
        val wf    = workflow(alice)
        every { userRepository.findByUsername("alice") } returns alice
        every { workflowRepository.findAllByOwnerId(alice.id!!) } returns listOf(wf)
        every { executionLogRepository.findLatestByWorkflowId(any()) } returns null

        val result = service.list("alice")

        assertTrue(result is Success)
        assertEquals(1, (result as Success).value.size)
    }

    @Test
    fun `list returns UserNotFound when user does not exist`() {
        every { userRepository.findByUsername("ghost") } returns null

        val result = service.list("ghost")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.UserNotFound, (result as Failure).value)
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    fun `getById returns workflow for owner`() {
        val alice = user()
        val wf    = workflow(alice)
        every { userRepository.findByUsername("alice") } returns alice
        every { workflowRepository.findByIdAndOwnerId(wf.id!!, alice.id!!) } returns wf
        every { executionLogRepository.findLatestByWorkflowId(wf.id!!) } returns null

        val result = service.getById(wf.id!!, "alice")

        assertTrue(result is Success)
        assertEquals("My WF", (result as Success).value.name)
    }

    @Test
    fun `getById returns WorkflowNotFound when id not found`() {
        val alice = user()
        val id    = UUID.randomUUID()
        every { userRepository.findByUsername("alice") } returns alice
        every { workflowRepository.findByIdAndOwnerId(id, alice.id!!) } returns null

        val result = service.getById(id, "alice")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.WorkflowNotFound, (result as Failure).value)
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    fun `create saves and returns a new workflow`() {
        val alice = user()
        val wf    = workflow(alice)
        every { userRepository.findByUsername("alice") } returns alice
        every { workflowRepository.save(any()) } returns wf
        every { executionLogRepository.findLatestByWorkflowId(any()) } returns null

        val result = service.create(WorkflowCreateRequest("My WF"), "alice")

        assertTrue(result is Success)
        verify(exactly = 1) { workflowRepository.save(any()) }
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    fun `update saves updated name`() {
        val alice = user()
        val wf    = workflow(alice)
        every { userRepository.findByUsername("alice") } returns alice
        every { workflowRepository.findByIdAndOwnerId(wf.id!!, alice.id!!) } returns wf
        every { workflowRepository.save(wf) } returns wf.apply { name = "New Name" }
        every { executionLogRepository.findLatestByWorkflowId(any()) } returns null

        val result = service.update(wf.id!!, WorkflowUpdateRequest("New Name"), "alice")

        assertTrue(result is Success)
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    fun `delete cascades and deletes workflow`() {
        val alice = user()
        val wf    = workflow(alice)
        every { userRepository.findByUsername("alice") } returns alice
        every { workflowRepository.findByIdAndOwnerId(wf.id!!, alice.id!!) } returns wf
        every { alertRepository.deleteAllByWorkflowId(wf.id!!) } returns Unit
        every { executionLogRepository.deleteAllByWorkflowId(wf.id!!) } returns Unit
        every { scheduleRepository.deleteAllByWorkflowId(wf.id!!) } returns Unit
        every { wtoRepository.deleteAllByWorkflowId(wf.id!!) } returns Unit
        every { taskRepository.deleteAllByWorkflowId(wf.id!!) } returns Unit
        every { workflowRepository.delete(wf) } returns Unit

        val result = service.delete(wf.id!!, "alice")

        assertTrue(result is Success)
        verify(exactly = 1) { workflowRepository.delete(wf) }
    }

    @Test
    fun `delete returns WorkflowNotFound when workflow not found`() {
        val alice = user()
        val id    = UUID.randomUUID()
        every { userRepository.findByUsername("alice") } returns alice
        every { workflowRepository.findByIdAndOwnerId(id, alice.id!!) } returns null

        val result = service.delete(id, "alice")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.WorkflowNotFound, (result as Failure).value)
    }
}
