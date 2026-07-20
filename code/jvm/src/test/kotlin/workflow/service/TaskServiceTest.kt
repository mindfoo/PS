package workflow.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.workflow.dto.TaskCreateRequest
import org.workflow.dto.TaskUpdateRequest
import org.workflow.entity.enums.RoleType
import org.workflow.entity.Roles
import org.workflow.entity.Task
import org.workflow.entity.User
import org.workflow.entity.Workflow
import org.workflow.entity.WorkflowTaskOrder
import org.workflow.repository.ExecutionRepository
import org.workflow.repository.TaskRepository
import org.workflow.repository.WorkflowRepository
import org.workflow.repository.WorkflowTaskOrderRepository
import org.workflow.service.ServiceHelpers
import org.workflow.service.TaskService
import org.workflow.service.utils.TaskError
import org.workflow.utils.Failure
import org.workflow.utils.Success
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.UUID

class TaskServiceTest {

    /* JUnit 5 injects a fresh temp directory before each test — used as scriptsBaseDir
       so listAvailableScripts tests can create real files without polluting the project tree. */
    @TempDir
    lateinit var tempDir: Path

    private lateinit var taskRepository: TaskRepository
    private lateinit var workflowRepository: WorkflowRepository
    private lateinit var wtoRepository: WorkflowTaskOrderRepository
    private lateinit var executionRepository: ExecutionRepository
    private lateinit var helpers: ServiceHelpers
    private lateinit var service: TaskService

    private fun role()     = Roles(id = UUID.randomUUID(), name = RoleType.WRITER)
    private fun user()     = User(id = UUID.randomUUID(), username = "alice", passwordValidation = "h", role = role())
    private fun workflow(owner: User) = Workflow(id = UUID.randomUUID(), name = "WF", createdBy = owner)
    private fun task(owner: User)     = Task(
        id = UUID.randomUUID(), name = "T", type = "SCRIPT",
        config = emptyMap(), workflow = null, createdBy = owner
    )

    @BeforeEach
    fun setup() {
        taskRepository     = mockk()
        workflowRepository = mockk()
        wtoRepository      = mockk()
        executionRepository = mockk()
        helpers            = mockk()
        every { helpers.isAdmin(any()) } answers { firstArg<User>().role.name == RoleType.ADMIN }
        service = TaskService(
            taskRepository, workflowRepository, wtoRepository, executionRepository, helpers,
            scriptsBaseDir = tempDir.toString()
        )
    }

    // listAll

    @Test
    fun `listAll returns all tasks for admin`() {
        val admin = User(id = UUID.randomUUID(), username = "admin", passwordValidation = "h",
            role = Roles(id = UUID.randomUUID(), name = RoleType.ADMIN))
        val t = task(admin)
        every { helpers.findUser("admin") } returns admin
        every { taskRepository.findAll() } returns listOf(t)
        every { wtoRepository.findAllByTaskIdIn(any()) } returns emptyList()

        val result = service.listAll("admin")

        assertTrue(result is Success)
        assertEquals(1, (result as Success).value.size)
    }

    @Test
    fun `listAll returns owned tasks for non-admin`() {
        val alice = user()
        val t     = task(alice)
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findAllPublic(alice.id!!) } returns listOf(t)
        every { wtoRepository.findAllByTaskIdIn(any()) } returns emptyList()

        val result = service.listAll("alice")

        assertTrue(result is Success)
    }

    @Test
    fun `listAll returns UserNotFound when user missing`() {
        every { helpers.findUser("ghost") } returns null

        val result = service.listAll("ghost")

        assertTrue(result is Failure)
        assertEquals(TaskError.UserNotFound, (result as Failure).value)
    }

    // getById

    @Test
    fun `getById returns task for owner`() {
        val alice = user()
        val t     = task(alice)
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns java.util.Optional.of(t)
        every { wtoRepository.findAllByTaskId(t.id!!) } returns emptyList()

        val result = service.getById(t.id!!, "alice")

        assertTrue(result is Success)
        assertEquals("T", (result as Success).value.name)
    }

    @Test
    fun `getById returns TaskNotFound when task missing`() {
        val alice = user()
        val id    = UUID.randomUUID()
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(id) } returns java.util.Optional.empty()

        val result = service.getById(id, "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.TaskNotFound, (result as Failure).value)
    }

    // create

    @Test
    fun `create returns saved task when no workflow specified`() {
        val alice = user()
        val t     = task(alice)
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.save(any()) } returns t

        val result = service.create(TaskCreateRequest("T", "SCRIPT"), "alice")

        assertTrue(result is Success)
        verify(exactly = 1) { taskRepository.save(any()) }
    }

    @Test
    fun `create returns WorkflowNotFound when workflowId supplied but workflow missing`() {
        val alice = user()
        val wfId  = UUID.randomUUID()
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.findById(wfId) } returns Optional.empty()

        val result = service.create(TaskCreateRequest("T", "SCRIPT", workflowId = wfId), "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.WorkflowNotFound, (result as Failure).value)
    }

    // update

    @Test
    fun `update returns updated task`() {
        val alice = user()
        val t     = task(alice)
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)
        every { taskRepository.save(t) } returns t.apply { name = "New" }

        val result = service.update(t.id!!, TaskUpdateRequest("New", "SCRIPT"), "alice")

        assertTrue(result is Success)
    }

    @Test
    fun `update returns TaskNotFound when task missing`() {
        val alice = user()
        val id    = UUID.randomUUID()
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(id) } returns Optional.empty()

        val result = service.update(id, TaskUpdateRequest("New", "SCRIPT"), "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.TaskNotFound, (result as Failure).value)
    }

    // delete

    @Test
    fun `delete removes task on success`() {
        val alice = user()
        val t     = task(alice)
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)
        every { executionRepository.deleteAllByTaskId(t.id!!) } returns Unit
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
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(id) } returns Optional.empty()

        val result = service.delete(id, "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.TaskNotFound, (result as Failure).value)
    }

    // listByWorkflow

    @Test
    fun `listByWorkflow returns ordered task entries for workflow`() {
        val alice = user()
        val wf    = workflow(alice)
        val t     = task(alice)
        val wto   = WorkflowTaskOrder(id = UUID.randomUUID(), workflow = wf, task = t, taskOrder = 1)
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.findById(wf.id!!) } returns Optional.of(wf)
        every { wtoRepository.findAllByWorkflowIdOrderByTaskOrderAsc(wf.id!!) } returns listOf(wto)

        val result = service.listByWorkflow(wf.id!!, "alice")

        assertTrue(result is Success)
        assertEquals(1, (result as Success).value.size)
    }

    @Test
    fun `listByWorkflow returns UserNotFound when user missing`() {
        every { helpers.findUser("ghost") } returns null

        val result = service.listByWorkflow(UUID.randomUUID(), "ghost")

        assertTrue(result is Failure)
        assertEquals(TaskError.UserNotFound, (result as Failure).value)
    }

    @Test
    fun `listByWorkflow returns WorkflowNotFound when workflow missing`() {
        val alice = user()
        val id    = UUID.randomUUID()
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.findById(id) } returns Optional.empty()

        val result = service.listByWorkflow(id, "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.WorkflowNotFound, (result as Failure).value)
    }

    // create with workflowId

    @Test
    fun `create with workflowId saves task and creates WorkflowTaskOrder`() {
        val alice = user()
        val wf    = workflow(alice)
        val t     = task(alice)
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.findById(wf.id!!) } returns Optional.of(wf)
        every { taskRepository.save(any()) } returns t
        every { wtoRepository.findAllByWorkflowIdOrderByTaskOrderAsc(wf.id!!) } returns emptyList()
        every { wtoRepository.save(any()) } returnsArgument 0

        val result = service.create(TaskCreateRequest("T", "SCRIPT", workflowId = wf.id!!), "alice")

        assertTrue(result is Success)
        verify(exactly = 1) { wtoRepository.save(any()) }
    }

    // linkToWorkflow

    @Test
    fun `linkToWorkflow returns success and saves WorkflowTaskOrder`() {
        val alice = user()
        val wf    = workflow(alice)
        val t     = task(alice)
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)
        every { workflowRepository.findById(wf.id!!) } returns Optional.of(wf)
        every { wtoRepository.findAllByWorkflowIdAndTaskId(wf.id!!, t.id!!) } returns emptyList()
        every { wtoRepository.findAllByWorkflowIdOrderByTaskOrderAsc(wf.id!!) } returns emptyList()
        every { wtoRepository.save(any()) } returnsArgument 0

        val result = service.linkToWorkflow(t.id!!, wf.id!!, "alice")

        assertTrue(result is Success)
        verify(exactly = 1) { wtoRepository.save(any()) }
    }

    @Test
    fun `linkToWorkflow returns AlreadyLinked when task already linked`() {
        val alice = user()
        val wf    = workflow(alice)
        val t     = task(alice)
        val wto   = WorkflowTaskOrder(id = UUID.randomUUID(), workflow = wf, task = t, taskOrder = 1)
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)
        every { workflowRepository.findById(wf.id!!) } returns Optional.of(wf)
        every { wtoRepository.findAllByWorkflowIdAndTaskId(wf.id!!, t.id!!) } returns listOf(wto)

        val result = service.linkToWorkflow(t.id!!, wf.id!!, "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.AlreadyLinked, (result as Failure).value)
    }

    @Test
    fun `linkToWorkflow returns TaskNotFound when task missing`() {
        val alice = user()
        val id    = UUID.randomUUID()
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(id) } returns Optional.empty()

        val result = service.linkToWorkflow(id, UUID.randomUUID(), "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.TaskNotFound, (result as Failure).value)
    }

    @Test
    fun `linkToWorkflow returns WorkflowNotFound when workflow missing`() {
        val alice = user()
        val t     = task(alice)
        val wfId  = UUID.randomUUID()
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)
        every { workflowRepository.findById(wfId) } returns Optional.empty()

        val result = service.linkToWorkflow(t.id!!, wfId, "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.WorkflowNotFound, (result as Failure).value)
    }

    @Test
    fun `linkToWorkflow returns UserNotFound when user missing`() {
        every { helpers.findUser("ghost") } returns null

        val result = service.linkToWorkflow(UUID.randomUUID(), UUID.randomUUID(), "ghost")

        assertTrue(result is Failure)
        assertEquals(TaskError.UserNotFound, (result as Failure).value)
    }

    // unlinkFromWorkflow

    @Test
    fun `unlinkFromWorkflow returns success and deletes link`() {
        val alice = user()
        val wf    = workflow(alice)
        val t     = task(alice)
        val wto   = WorkflowTaskOrder(id = UUID.randomUUID(), workflow = wf, task = t, taskOrder = 1)
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)
        every { workflowRepository.findById(wf.id!!) } returns Optional.of(wf)
        every { wtoRepository.findAllByWorkflowIdAndTaskId(wf.id!!, t.id!!) } returns listOf(wto)
        every { wtoRepository.deleteAll(listOf(wto)) } returns Unit

        val result = service.unlinkFromWorkflow(t.id!!, wf.id!!, "alice")

        assertTrue(result is Success)
        verify(exactly = 1) { wtoRepository.deleteAll(listOf(wto)) }
    }

    @Test
    fun `unlinkFromWorkflow clears direct FK when task was created inside the workflow`() {
        val alice = user()
        val wf    = workflow(alice)
        // Task was created with workflow set (the direct FK case)
        val t     = Task(id = UUID.randomUUID(), name = "T", type = "SCRIPT",
                         config = emptyMap(), workflow = wf, createdBy = alice)
        val wto   = WorkflowTaskOrder(id = UUID.randomUUID(), workflow = wf, task = t, taskOrder = 1)
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)
        every { workflowRepository.findById(wf.id!!) } returns Optional.of(wf)
        every { wtoRepository.findAllByWorkflowIdAndTaskId(wf.id!!, t.id!!) } returns listOf(wto)
        every { wtoRepository.deleteAll(listOf(wto)) } returns Unit
        every { taskRepository.save(t) } returns t

        val result = service.unlinkFromWorkflow(t.id!!, wf.id!!, "alice")

        assertTrue(result is Success)
        assertNull(t.workflow)
        verify(exactly = 1) { taskRepository.save(t) }
    }

    @Test
    fun `unlinkFromWorkflow returns NotLinked when no link found`() {
        val alice = user()
        val wf    = workflow(alice)
        val t     = task(alice)
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)
        every { workflowRepository.findById(wf.id!!) } returns Optional.of(wf)
        every { wtoRepository.findAllByWorkflowIdAndTaskId(wf.id!!, t.id!!) } returns emptyList()

        val result = service.unlinkFromWorkflow(t.id!!, wf.id!!, "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.NotLinked, (result as Failure).value)
    }

    @Test
    fun `unlinkFromWorkflow returns TaskNotFound when task missing`() {
        val alice = user()
        val id    = UUID.randomUUID()
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(id) } returns Optional.empty()

        val result = service.unlinkFromWorkflow(id, UUID.randomUUID(), "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.TaskNotFound, (result as Failure).value)
    }

    @Test
    fun `unlinkFromWorkflow returns WorkflowNotFound when workflow missing`() {
        val alice = user()
        val t     = task(alice)
        val wfId  = UUID.randomUUID()
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)
        every { workflowRepository.findById(wfId) } returns Optional.empty()

        val result = service.unlinkFromWorkflow(t.id!!, wfId, "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.WorkflowNotFound, (result as Failure).value)
    }

    @Test
    fun `unlinkFromWorkflow returns UserNotFound when user missing`() {
        every { helpers.findUser("ghost") } returns null

        val result = service.unlinkFromWorkflow(UUID.randomUUID(), UUID.randomUUID(), "ghost")

        assertTrue(result is Failure)
        assertEquals(TaskError.UserNotFound, (result as Failure).value)
    }

    // listAvailableScripts

    @Test
    fun `listAvailableScripts returns sorted filenames directly in scriptsBaseDir`() {
        Files.createFile(tempDir.resolve("b.py"))
        Files.createFile(tempDir.resolve("a.sh"))

        assertEquals(listOf("a.sh", "b.py"), service.listAvailableScripts())
    }

    @Test
    fun `listAvailableScripts excludes subdirectories`() {
        val subDir = Files.createDirectory(tempDir.resolve("subdir"))
        Files.createFile(subDir.resolve("nested.py"))
        Files.createFile(tempDir.resolve("shared.py"))

        assertEquals(listOf("shared.py"), service.listAvailableScripts())
    }

    @Test
    fun `listAvailableScripts returns empty list when directory does not exist`() {
        service = TaskService(
            taskRepository, workflowRepository, wtoRepository, executionRepository, helpers,
            scriptsBaseDir = tempDir.resolve("does-not-exist").toString()
        )

        assertEquals(emptyList<String>(), service.listAvailableScripts())
    }
}
