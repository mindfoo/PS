package workflow.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.workflow.dto.RetryPolicyUpdateRequest
import org.workflow.dto.TaskOrderItem
import org.workflow.dto.TaskReorderRequest
import org.workflow.dto.WorkflowCreateRequest
import org.workflow.dto.WorkflowUpdateRequest
import org.workflow.entity.Execution
import org.workflow.entity.enums.RoleType
import org.workflow.entity.Roles
import org.workflow.entity.Task
import org.workflow.entity.WorkflowTaskOrder
import java.time.LocalDateTime
import org.workflow.entity.User
import org.workflow.entity.Workflow
import org.workflow.repository.ExecutionRepository
import org.workflow.repository.ScheduleRepository
import org.workflow.repository.TaskRepository
import org.workflow.repository.WorkflowRepository
import org.workflow.repository.WorkflowTaskOrderRepository
import org.workflow.service.ServiceHelpers
import org.workflow.service.WorkflowService
import org.workflow.service.utils.WorkflowError
import org.workflow.utils.Failure
import org.workflow.utils.Success
import java.util.Optional
import java.util.UUID

class WorkflowServiceTest {

    private lateinit var workflowRepository: WorkflowRepository
    private lateinit var wtoRepository: WorkflowTaskOrderRepository
    private lateinit var executionRepository: ExecutionRepository
    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var taskRepository: TaskRepository
    private lateinit var helpers: ServiceHelpers
    private lateinit var service: WorkflowService

    private fun readerRole() = Roles(id = UUID.randomUUID(), name = RoleType.READER)
    private fun adminRole()  = Roles(id = UUID.randomUUID(), name = RoleType.ADMIN)
    private fun user(role: Roles = readerRole(), name: String = "alice") =
        User(id = UUID.randomUUID(), username = name, passwordValidation = "h", role = role)
    private fun workflow(owner: User) =
        Workflow(id = UUID.randomUUID(), name = "My WF", createdBy = owner)

    @BeforeEach
    fun setup() {
        workflowRepository    = mockk()
        wtoRepository         = mockk()
        executionRepository = mockk()
        scheduleRepository    = mockk()
        taskRepository        = mockk()
        helpers              = mockk()
        every { helpers.isAdmin(any()) } answers { firstArg<User>().role.name == RoleType.ADMIN }
        service = WorkflowService(
            workflowRepository, wtoRepository,
            executionRepository, scheduleRepository, taskRepository, helpers
        )
    }

    // list

    @Test
    fun `list returns all workflows for admin`() {
        val admin = user(adminRole(), "admin")
        val wf    = workflow(admin)
        every { helpers.findUser("admin") } returns admin
        every { workflowRepository.findAll() } returns listOf(wf)
        every { executionRepository.findLatestByWorkflowId(any()) } returns null

        val result = service.list("admin")

        assertTrue(result is Success)
        assertEquals(1, (result as Success).value.size)
    }

    @Test
    fun `list returns all workflows for non-admin`() {
        val alice = user()
        val wf    = workflow(alice)
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.findAllVisible(alice.id!!) } returns listOf(wf)
        every { executionRepository.findLatestByWorkflowId(any()) } returns null

        val result = service.list("alice")

        assertTrue(result is Success)
        assertEquals(1, (result as Success).value.size)
    }

    @Test
    fun `list returns UserNotFound when user does not exist`() {
        every { helpers.findUser("ghost") } returns null

        val result = service.list("ghost")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.UserNotFound, (result as Failure).value)
    }

    // getById

    @Test
    fun `getById returns workflow for any authenticated user`() {
        val alice = user()
        val wf    = workflow(alice)
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.findById(wf.id!!) } returns Optional.of(wf)
        every { executionRepository.findLatestByWorkflowId(wf.id!!) } returns null

        val result = service.getById(wf.id!!, "alice")

        assertTrue(result is Success)
        assertEquals("My WF", (result as Success).value.name)
    }

    @Test
    fun `getById returns WorkflowNotFound when id not found`() {
        val alice = user()
        val id    = UUID.randomUUID()
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.findById(id) } returns Optional.empty()

        val result = service.getById(id, "alice")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.WorkflowNotFound, (result as Failure).value)
    }

    // create

    @Test
    fun `create saves and returns a new workflow`() {
        val alice = user()
        val wf    = workflow(alice)
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.save(any()) } returns wf
        every { executionRepository.findLatestByWorkflowId(any()) } returns null

        val result = service.create(WorkflowCreateRequest("My WF"), "alice")

        assertTrue(result is Success)
        verify(exactly = 1) { workflowRepository.save(any()) }
    }

    // update

    @Test
    fun `update saves updated name`() {
        val alice = user()
        val wf    = workflow(alice)
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.findByIdAndOwnerId(wf.id!!, alice.id!!) } returns wf
        every { workflowRepository.save(wf) } returns wf.apply { name = "New Name" }
        every { executionRepository.findLatestByWorkflowId(any()) } returns null

        val result = service.update(wf.id!!, WorkflowUpdateRequest("New Name"), "alice")

        assertTrue(result is Success)
    }

    // delete

    @Test
    fun `delete cascades and deletes workflow`() {
        val alice = user()
        val wf    = workflow(alice)
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.findByIdAndOwnerId(wf.id!!, alice.id!!) } returns wf
        every { executionRepository.deleteAllByWorkflowId(wf.id!!) } returns Unit
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
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.findByIdAndOwnerId(id, alice.id!!) } returns null

        val result = service.delete(id, "alice")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.WorkflowNotFound, (result as Failure).value)
    }

    // admin branch: getById

    @Test
    fun `getById returns workflow for admin using findById`() {
        val admin = user(adminRole(), "admin")
        val wf    = workflow(admin)
        every { helpers.findUser("admin") } returns admin
        every { workflowRepository.findById(wf.id!!) } returns Optional.of(wf)
        every { executionRepository.findLatestByWorkflowId(wf.id!!) } returns null

        val result = service.getById(wf.id!!, "admin")

        assertTrue(result is Success)
    }

    // create: UserNotFound

    @Test
    fun `create returns UserNotFound when user does not exist`() {
        every { helpers.findUser("ghost") } returns null

        val result = service.create(WorkflowCreateRequest("WF"), "ghost")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.UserNotFound, (result as Failure).value)
    }

    // update: missing branches

    @Test
    fun `update returns UserNotFound when user does not exist`() {
        every { helpers.findUser("ghost") } returns null

        val result = service.update(UUID.randomUUID(), WorkflowUpdateRequest("New"), "ghost")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.UserNotFound, (result as Failure).value)
    }

    @Test
    fun `update returns WorkflowNotFound when workflow missing for non-admin`() {
        val alice = user()
        val id    = UUID.randomUUID()
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.findByIdAndOwnerId(id, alice.id!!) } returns null

        val result = service.update(id, WorkflowUpdateRequest("New"), "alice")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.WorkflowNotFound, (result as Failure).value)
    }

    // delete: UserNotFound

    @Test
    fun `delete returns UserNotFound when user does not exist`() {
        every { helpers.findUser("ghost") } returns null

        val result = service.delete(UUID.randomUUID(), "ghost")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.UserNotFound, (result as Failure).value)
    }

    // listExecutions

    @Test
    fun `listExecutions returns list for workflow owner`() {
        val alice = user()
        val wf    = workflow(alice)
        val exec  = Execution(
            id = UUID.randomUUID(), triggeredType = "MANUAL", type = "WORKFLOW",
            status = "SUCCESS", triggeredBy = alice, workflow = wf,
            startedAt = LocalDateTime.now()
        )
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.findById(wf.id!!) } returns Optional.of(wf)
        every { executionRepository.findTopLevelByWorkflowIdOrderByStartedAtDesc(wf.id!!) } returns listOf(exec)
        every { executionRepository.findAllByParentExecutionIdOrderByStartedAtAsc(exec.id!!) } returns emptyList()

        val result = service.listExecutions(wf.id!!, "alice")

        assertTrue(result is Success)
        assertEquals(1, (result as Success).value.size)
    }

    @Test
    fun `listExecutions returns UserNotFound when user missing`() {
        every { helpers.findUser("ghost") } returns null

        val result = service.listExecutions(UUID.randomUUID(), "ghost")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.UserNotFound, (result as Failure).value)
    }

    @Test
    fun `listExecutions returns WorkflowNotFound when workflow missing`() {
        val alice = user()
        val id    = UUID.randomUUID()
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.findById(id) } returns Optional.empty()

        val result = service.listExecutions(id, "alice")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.WorkflowNotFound, (result as Failure).value)
    }

    // getExecution

    @Test
    fun `getExecution returns summary when execution found`() {
        val alice  = user()
        val wf     = workflow(alice)
        val execId = UUID.randomUUID()
        val exec   = Execution(
            id = execId, triggeredType = "MANUAL", type = "WORKFLOW",
            status = "SUCCESS", triggeredBy = alice, workflow = wf,
            startedAt = LocalDateTime.now()
        )
        every { helpers.findUser("alice") } returns alice
        every { executionRepository.findById(execId) } returns Optional.of(exec)
        every { executionRepository.findAllByParentExecutionIdOrderByStartedAtAsc(execId) } returns emptyList()

        val result = service.getExecution(execId, "alice")

        assertTrue(result is Success)
        assertEquals("SUCCESS", (result as Success).value.status)
    }

    @Test
    fun `getExecution returns UserNotFound when user missing`() {
        every { helpers.findUser("ghost") } returns null

        val result = service.getExecution(UUID.randomUUID(), "ghost")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.UserNotFound, (result as Failure).value)
    }

    @Test
    fun `getExecution returns ExecutionNotFound when execution does not exist`() {
        val alice  = user()
        val execId = UUID.randomUUID()
        every { helpers.findUser("alice") } returns alice
        every { executionRepository.findById(execId) } returns Optional.empty()

        val result = service.getExecution(execId, "alice")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.ExecutionNotFound, (result as Failure).value)
    }

    // reorderTasks

    @Test
    fun `reorderTasks updates taskOrder and returns success`() {
        val alice   = user()
        val wf      = workflow(alice)
        val t       = Task(id = UUID.randomUUID(), name = "T", type = "SCRIPT", config = emptyMap(), workflow = wf, createdBy = alice)
        val orderId = UUID.randomUUID()
        val wto     = WorkflowTaskOrder(id = orderId, workflow = wf, task = t, taskOrder = 1)
        val request = TaskReorderRequest(items = listOf(TaskOrderItem(orderId = orderId, taskOrder = 2)))
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.findByIdAndOwnerId(wf.id!!, alice.id!!) } returns wf
        every { wtoRepository.findAllByWorkflowIdOrderByTaskOrderAsc(wf.id!!) } returns listOf(wto)
        every { wtoRepository.saveAll(any<Iterable<WorkflowTaskOrder>>()) } returns mutableListOf(wto)

        val result = service.reorderTasks(wf.id!!, request, "alice")

        assertTrue(result is Success)
        assertEquals(2, wto.taskOrder)
    }

    @Test
    fun `reorderTasks returns UserNotFound when user missing`() {
        every { helpers.findUser("ghost") } returns null

        val result = service.reorderTasks(UUID.randomUUID(), TaskReorderRequest(emptyList()), "ghost")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.UserNotFound, (result as Failure).value)
    }

    @Test
    fun `reorderTasks returns WorkflowNotFound when workflow missing`() {
        val alice   = user()
        val id      = UUID.randomUUID()
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.findByIdAndOwnerId(id, alice.id!!) } returns null

        val result = service.reorderTasks(id, TaskReorderRequest(emptyList()), "alice")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.WorkflowNotFound, (result as Failure).value)
    }

    // updateRetryPolicy

    @Test
    fun `updateRetryPolicy updates retry count and returns success`() {
        val alice   = user()
        val wf      = workflow(alice)
        val t       = Task(id = UUID.randomUUID(), name = "T", type = "SCRIPT", config = emptyMap(), workflow = wf, createdBy = alice)
        val wto     = WorkflowTaskOrder(id = UUID.randomUUID(), workflow = wf, task = t, taskOrder = 1, retryPolicy = 0)
        val request = RetryPolicyUpdateRequest(retryPolicy = 3)
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.findByIdAndOwnerId(wf.id!!, alice.id!!) } returns wf
        every { wtoRepository.findByWorkflowIdAndTaskId(wf.id!!, t.id!!) } returns wto
        every { wtoRepository.save(wto) } returns wto

        val result = service.updateRetryPolicy(wf.id!!, t.id!!, request, "alice")

        assertTrue(result is Success)
        assertEquals(3, wto.retryPolicy)
    }

    @Test
    fun `updateRetryPolicy returns UserNotFound when user missing`() {
        every { helpers.findUser("ghost") } returns null

        val result = service.updateRetryPolicy(UUID.randomUUID(), UUID.randomUUID(), RetryPolicyUpdateRequest(1), "ghost")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.UserNotFound, (result as Failure).value)
    }

    @Test
    fun `updateRetryPolicy returns WorkflowNotFound when order row missing`() {
        val alice  = user()
        val wf     = workflow(alice)
        val taskId = UUID.randomUUID()
        every { helpers.findUser("alice") } returns alice
        every { workflowRepository.findByIdAndOwnerId(wf.id!!, alice.id!!) } returns wf
        every { wtoRepository.findByWorkflowIdAndTaskId(wf.id!!, taskId) } returns null

        val result = service.updateRetryPolicy(wf.id!!, taskId, RetryPolicyUpdateRequest(2), "alice")

        assertTrue(result is Failure)
        assertEquals(WorkflowError.TaskNotLinked, (result as Failure).value)
    }
}
