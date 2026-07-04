package workflow.service

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.springframework.web.multipart.MultipartFile
import org.workflow.dto.TaskCreateRequest
import org.workflow.dto.TaskUpdateRequest
import org.workflow.entity.enums.RoleType
import org.workflow.entity.Roles
import org.workflow.entity.Task
import org.workflow.entity.User
import org.workflow.entity.Workflow
import org.workflow.entity.WorkflowTaskOrder
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
       so upload tests can create/read real files without polluting the project tree. */
    @TempDir
    lateinit var tempDir: Path

    private lateinit var taskRepository: TaskRepository
    private lateinit var workflowRepository: WorkflowRepository
    private lateinit var wtoRepository: WorkflowTaskOrderRepository
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
        helpers            = mockk()
        every { helpers.isAdmin(any()) } answers { firstArg<User>().role.name == RoleType.ADMIN }
        service = TaskService(
            taskRepository, workflowRepository, wtoRepository, helpers,
            scriptsBaseDir = tempDir.toString(),
            maxScriptSizeMb = 10L
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
        every { taskRepository.findAllVisible(alice.id!!) } returns listOf(t)
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
        every { workflowRepository.findByIdAndOwnerId(wfId, alice.id!!) } returns null

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
        every { taskRepository.findByIdAndOwnerId(t.id!!, alice.id!!) } returns t
        every { taskRepository.save(t) } returns t.apply { name = "New" }

        val result = service.update(t.id!!, TaskUpdateRequest("New", "SCRIPT"), "alice")

        assertTrue(result is Success)
    }

    @Test
    fun `update returns TaskNotFound when task missing`() {
        val alice = user()
        val id    = UUID.randomUUID()
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findByIdAndOwnerId(id, alice.id!!) } returns null

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
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findByIdAndOwnerId(id, alice.id!!) } returns null

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
        every { taskRepository.findAllByWorkflowId(wf.id!!) } returns emptyList()

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
        every { workflowRepository.findByIdAndOwnerId(wf.id!!, alice.id!!) } returns wf
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
        every { taskRepository.findByIdAndOwnerId(t.id!!, alice.id!!) } returns t
        every { workflowRepository.findByIdAndOwnerId(wf.id!!, alice.id!!) } returns wf
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
        every { taskRepository.findByIdAndOwnerId(t.id!!, alice.id!!) } returns t
        every { workflowRepository.findByIdAndOwnerId(wf.id!!, alice.id!!) } returns wf
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
        every { taskRepository.findByIdAndOwnerId(id, alice.id!!) } returns null

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
        every { taskRepository.findByIdAndOwnerId(t.id!!, alice.id!!) } returns t
        every { workflowRepository.findByIdAndOwnerId(wfId, alice.id!!) } returns null

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
        every { taskRepository.findByIdAndOwnerId(t.id!!, alice.id!!) } returns t
        every { workflowRepository.findByIdAndOwnerId(wf.id!!, alice.id!!) } returns wf
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
        every { taskRepository.findByIdAndOwnerId(t.id!!, alice.id!!) } returns t
        every { workflowRepository.findByIdAndOwnerId(wf.id!!, alice.id!!) } returns wf
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
        every { taskRepository.findByIdAndOwnerId(t.id!!, alice.id!!) } returns t
        every { workflowRepository.findByIdAndOwnerId(wf.id!!, alice.id!!) } returns wf
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
        every { taskRepository.findByIdAndOwnerId(id, alice.id!!) } returns null

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
        every { taskRepository.findByIdAndOwnerId(t.id!!, alice.id!!) } returns t
        every { workflowRepository.findByIdAndOwnerId(wfId, alice.id!!) } returns null

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

    // uploadScript

    @Test
    fun `uploadScript saves file and returns 201 metadata`() {
        val alice = user()
        val t     = task(alice)
        val file  = mockk<MultipartFile>()
        every { file.originalFilename } returns "script.py"
        every { file.size } returns 100L
        every { file.transferTo(any<Path>()) } just Runs
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)
        every { taskRepository.save(any()) } returns t

        val result = service.uploadScript(t.id!!, file, "alice")

        assertTrue(result is Success)
        assertEquals("script.py", (result as Success).value.fileName)
        verify(exactly = 1) { taskRepository.save(any()) }
    }

    @Test
    fun `uploadScript returns UserNotFound when user missing`() {
        every { helpers.findUser("ghost") } returns null

        val result = service.uploadScript(UUID.randomUUID(), mockk(), "ghost")

        assertTrue(result is Failure)
        assertEquals(TaskError.UserNotFound, (result as Failure).value)
    }

    @Test
    fun `uploadScript returns TaskNotFound when task missing`() {
        val alice = user()
        val id    = UUID.randomUUID()
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(id) } returns Optional.empty()

        val result = service.uploadScript(id, mockk(), "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.TaskNotFound, (result as Failure).value)
    }

    @Test
    fun `uploadScript returns AccessDenied for private task owned by another user`() {
        val alice = user()
        val bob   = User(id = UUID.randomUUID(), username = "bob", passwordValidation = "h", role = role())
        val t     = Task(id = UUID.randomUUID(), name = "T", type = "SCRIPT",
                         config = emptyMap(), workflow = null, createdBy = bob, isPrivate = true)
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)

        val result = service.uploadScript(t.id!!, mockk(), "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.AccessDenied, (result as Failure).value)
    }

    @Test
    fun `uploadScript returns InvalidFileType when filename is blank`() {
        val alice = user()
        val t     = task(alice)
        val file  = mockk<MultipartFile>()
        every { file.originalFilename } returns ""
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)

        val result = service.uploadScript(t.id!!, file, "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.InvalidFileType, (result as Failure).value)
    }

    @Test
    fun `uploadScript returns InvalidFileType when extension is not allowed`() {
        val alice = user()
        val t     = task(alice)
        val file  = mockk<MultipartFile>()
        every { file.originalFilename } returns "virus.exe"
        every { file.size } returns 100L
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)

        val result = service.uploadScript(t.id!!, file, "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.InvalidFileType, (result as Failure).value)
    }

    @Test
    fun `uploadScript returns FileTooLarge when file exceeds limit`() {
        val alice = user()
        val t     = task(alice)
        val file  = mockk<MultipartFile>()
        every { file.originalFilename } returns "script.py"
        every { file.size } returns 11L * 1024 * 1024  // 11 MB > 10 MB limit
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)

        val result = service.uploadScript(t.id!!, file, "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.FileTooLarge, (result as Failure).value)
    }

    // getScriptInfo

    @Test
    fun `getScriptInfo returns metadata when script exists on disk`() {
        val alice = user()
        val t     = task(alice).also { it.scriptFileName = "script.py" }
        /* Create the actual file in the temp dir so file.exists() returns true */
        val scriptDir = tempDir.resolve(t.id.toString())
        Files.createDirectories(scriptDir)
        Files.writeString(scriptDir.resolve("script.py"), "print('hello')")
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)

        val result = service.getScriptInfo(t.id!!, "alice")

        assertTrue(result is Success)
        assertEquals("script.py", (result as Success).value.fileName)
        assertTrue((result).value.sizeBytes > 0)
    }

    @Test
    fun `getScriptInfo returns ScriptNotFound when no scriptFileName in DB`() {
        val alice = user()
        val t     = task(alice) // scriptFileName is null
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)

        val result = service.getScriptInfo(t.id!!, "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.ScriptNotFound, (result as Failure).value)
    }

    @Test
    fun `getScriptInfo returns ScriptNotFound when file missing from disk`() {
        val alice = user()
        val t     = task(alice).also { it.scriptFileName = "script.py" }
        // No file created in tempDir — disk is empty
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)

        val result = service.getScriptInfo(t.id!!, "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.ScriptNotFound, (result as Failure).value)
    }

    @Test
    fun `getScriptInfo returns UserNotFound when user missing`() {
        every { helpers.findUser("ghost") } returns null

        val result = service.getScriptInfo(UUID.randomUUID(), "ghost")

        assertTrue(result is Failure)
        assertEquals(TaskError.UserNotFound, (result as Failure).value)
    }

    @Test
    fun `getScriptInfo returns TaskNotFound when task missing`() {
        val alice = user()
        val id    = UUID.randomUUID()
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(id) } returns Optional.empty()

        val result = service.getScriptInfo(id, "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.TaskNotFound, (result as Failure).value)
    }

    @Test
    fun `getScriptInfo returns AccessDenied for private task owned by another user`() {
        val alice = user()
        val bob   = User(id = UUID.randomUUID(), username = "bob", passwordValidation = "h", role = role())
        val t     = Task(id = UUID.randomUUID(), name = "T", type = "SCRIPT",
                         config = emptyMap(), workflow = null, createdBy = bob, isPrivate = true)
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findById(t.id!!) } returns Optional.of(t)

        val result = service.getScriptInfo(t.id!!, "alice")

        assertTrue(result is Failure)
        assertEquals(TaskError.AccessDenied, (result as Failure).value)
    }
}
