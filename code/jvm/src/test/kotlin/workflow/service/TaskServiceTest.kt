package workflow.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.workflow.dto.TaskCreateRequest
import org.workflow.dto.TaskUpdateRequest
import org.workflow.entity.Roles
import org.workflow.entity.Task
import org.workflow.entity.User
import org.workflow.entity.Workflow
import org.workflow.repository.TaskRepository
import org.workflow.repository.UserRepository
import org.workflow.repository.WorkflowRepository
import org.workflow.repository.WorkflowTaskOrderRepository
import org.workflow.service.TaskService
import org.workflow.service.utils.TaskError
import org.workflow.utils.Failure
import org.workflow.utils.Success
import java.util.Optional
import java.util.UUID

class TaskServiceTest {

    private lateinit var taskRepository: TaskRepository
    private lateinit var workflowRepository: WorkflowRepository
    private lateinit var userRepository: UserRepository
    private lateinit var wtoRepository: WorkflowTaskOrderRepository
    private lateinit var service: TaskService

    private fun role()     = Roles(id = UUID.randomUUID(), name = "WRITER")
    private fun user()     = User(id = UUID.randomUUID(), username = "alice", passwordValidation = "h", role = role())
    private fun workflow(owner: User) = Workflow(id = UUID.randomUUID(), name = "WF", created_by = owner)
    private fun task(owner: User)     = Task(
        id = UUID.randomUUID(), name = "T", type = "SCRIPT",
        config = emptyMap(), workflow_id = null, createdBy = owner
    )

    @BeforeEach
    fun setup() {
        taskRepository     = mockk()
        workflowRepository = mockk()
        userRepository     = mockk()
        wtoRepository      = mockk()
        service = TaskService(taskRepository, workflowRepository, userRepository, wtoRepository)
    }

    // ── listAll ───────────────────────────────────────────────────────────────

    @Test
    fun `listAll returns all tasks for admin`() {
        val admin = User(id = UUID.randomUUID(), username = "admin", passwordValidation = "h",
            role = Roles(id = UUID.randomUUID(), name = "ADMIN"))
        val t = task(admin)
        every { userRepository.findByUsername("admin") } returns admin
        every { taskRepository.findAll() } returns listOf(t)

        val result = service.listAll("admin")

        assertTrue(result is Success)
        assertEquals(1, (result as Success).value.size)
    }

    @Test
    fun `listAll returns owned tasks for non-admin`() {
        val alice = user()
        val t     = task(alice)
        every { userRepository.findByUsername("alice") } returns alice
        every { taskRepository.findAllByCreatedById(alice.id!!) } returns listOf(t)

        val result = service.listAll("alice")

        assertTrue(result is Success)
    }

    @Test
    fun `listAll returns UserNotFound when user missing`() {
        every { userRepository.findByUsername("ghost") } returns null

        val result = service.listAll("ghost")

        assertTrue(result is Failure)
        assertEquals(TaskError.UserNotFound, (result as Failure).value)
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    fun `getById returns task for owner`() {
        val alice = user()
        val t     = task(alice)
        every { userRepository.findByUsername("alice") } returns alice
        every { taskRepository.findByIdAndOwnerId(t.id!!, alice.id!!) } returns t

        val result = service.getById(t.id!!, "alice")

        assertTrue(result is Success)
        assertEquals("T", (result as Success).value.name)
    }

    @Test
    fun `getById returns TaskNotFound when task missing`() {
        val alice = user()
        val id    = UUID.randomUUID()
        every { userRepository.findByUsername("alice") } returns alice
        every { taskRepository.findByIdAndOwnerId(id, alice.id!!) } returns null

        val result = service.getById(id, "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.TaskNotFound, (result as Failure).value)
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    fun `create returns saved task when no workflow specified`() {
        val alice = user()
        val t     = task(alice)
        every { userRepository.findByUsername("alice") } returns alice
        every { taskRepository.save(any()) } returns t

        val result = service.create(TaskCreateRequest("T", "SCRIPT"), "alice")

        assertTrue(result is Success)
        verify(exactly = 1) { taskRepository.save(any()) }
    }

    @Test
    fun `create returns WorkflowNotFound when workflowId supplied but workflow missing`() {
        val alice = user()
        val wfId  = UUID.randomUUID()
        every { userRepository.findByUsername("alice") } returns alice
        every { workflowRepository.findByIdAndOwnerId(wfId, alice.id!!) } returns null

        val result = service.create(TaskCreateRequest("T", "SCRIPT", workflowId = wfId), "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.WorkflowNotFound, (result as Failure).value)
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    fun `update returns updated task`() {
        val alice = user()
        val t     = task(alice)
        every { userRepository.findByUsername("alice") } returns alice
        every { taskRepository.findByIdAndOwnerId(t.id!!, alice.id!!) } returns t
        every { taskRepository.save(t) } returns t.apply { name = "New" }

        val result = service.update(t.id!!, TaskUpdateRequest("New", "SCRIPT"), "alice")

        assertTrue(result is Success)
    }

    @Test
    fun `update returns TaskNotFound when task missing`() {
        val alice = user()
        val id    = UUID.randomUUID()
        every { userRepository.findByUsername("alice") } returns alice
        every { taskRepository.findByIdAndOwnerId(id, alice.id!!) } returns null

        val result = service.update(id, TaskUpdateRequest("New", "SCRIPT"), "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.TaskNotFound, (result as Failure).value)
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    fun `delete removes task on success`() {
        val alice = user()
        val t     = task(alice)
        every { userRepository.findByUsername("alice") } returns alice
        every { taskRepository.findByIdAndOwnerId(t.id!!, alice.id!!) } returns t
        every { wtoRepository.findAllByTaskId(t.id!!) } returns emptyList()
        every { wtoRepository.deleteAll(emptyList()) } returns Unit
        every { taskRepository.delete(t) } returns Unit

        val result = service.delete(t.id!!, "alice")

        assertTrue(result is Success)
        verify(exactly = 1) { taskRepository.delete(t) }
    }

    @Test
    fun `delete returns TaskNotFound when task missing`() {
        val alice = user()
        val id    = UUID.randomUUID()
        every { userRepository.findByUsername("alice") } returns alice
        every { taskRepository.findByIdAndOwnerId(id, alice.id!!) } returns null

        val result = service.delete(id, "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.TaskNotFound, (result as Failure).value)
    }
}
