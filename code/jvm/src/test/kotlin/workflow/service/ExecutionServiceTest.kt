package workflow.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.RestTemplate
import org.workflow.entity.Execution
import org.workflow.entity.ExecutionStatus
import org.workflow.entity.ExecutionTriggerType
import org.workflow.entity.ExecutionType
import org.workflow.entity.Roles
import org.workflow.entity.Task
import org.workflow.entity.User
import org.workflow.repository.ExecutionLogRepository
import org.workflow.repository.TaskRepository
import org.workflow.repository.WorkflowRepository
import org.workflow.repository.WorkflowTaskOrderRepository
import org.workflow.service.ExecutionService
import org.workflow.service.ServiceHelpers
import org.workflow.service.utils.ExecutionError
import org.workflow.utils.Failure
import org.workflow.utils.Success
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID
import java.util.concurrent.Executors

class ExecutionServiceTest {

    private lateinit var executionLogRepository: ExecutionLogRepository
    private lateinit var taskRepository: TaskRepository
    private lateinit var workflowRepository: WorkflowRepository
    private lateinit var wtoRepository: WorkflowTaskOrderRepository
    private lateinit var restTemplate: RestTemplate
    private lateinit var helpers: ServiceHelpers
    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var service: ExecutionService

    private val userId  = UUID.randomUUID()
    private val bobId   = UUID.randomUUID()
    private val taskId  = UUID.randomUUID()
    private val execId  = UUID.randomUUID()

    private fun role(name: String = "WRITER") = Roles(id = UUID.randomUUID(), name = name)
    private fun user(r: Roles = role()) = User(id = userId, username = "alice", passwordValidation = "h", role = r)
    private fun adminUser() = User(id = UUID.randomUUID(), username = "admin", passwordValidation = "h", role = role("ADMIN"))
    private fun bob()       = User(id = bobId,  username = "bob",   passwordValidation = "h", role = role())

    private fun task(owner: User) = Task(id = taskId, name = "MyTask", type = "SCRIPT", config = emptyMap(), createdBy = owner)

    private fun execution(owner: User, status: String) = Execution(
        id = execId,
        triggeredType = ExecutionTriggerType.MANUAL,
        type = ExecutionType.WORKFLOW,
        status = status,
        startedAt = LocalDateTime.now(),
        triggeredBy = owner,
        workflow = null
    )

    @BeforeEach
    fun setup() {
        executionLogRepository = mockk()
        taskRepository         = mockk()
        workflowRepository     = mockk()
        wtoRepository          = mockk()
        restTemplate           = mockk()
        helpers                = mockk()
        jdbcTemplate           = mockk(relaxed = true)
        every { helpers.isAdmin(any()) } answers { firstArg<User>().role.name.equals("ADMIN", ignoreCase = true) }
        service = ExecutionService(
            executionLogRepository, workflowRepository, wtoRepository, taskRepository,
            restTemplate, helpers,
            Executors.newVirtualThreadPerTaskExecutor(),
            jdbcTemplate,
            ObjectMapper()
        )
    }

    // cancelExecution

    @Test
    fun `cancelExecution returns true and marks execution CANCELED for owner`() {
        val alice = user()
        val exec  = execution(alice, ExecutionStatus.RUNNING)
        every { helpers.findUser("alice") } returns alice
        every { executionLogRepository.findById(execId) } returns Optional.of(exec)
        every { executionLogRepository.findAllByParentExecutionIdOrderByStartedAtAsc(execId) } returns emptyList()
        every { executionLogRepository.save(exec) } returns exec

        val result = service.cancelExecution(execId, "alice")

        assertTrue(result)
        assertEquals(ExecutionStatus.CANCELED, exec.status)
        verify(exactly = 1) { executionLogRepository.save(exec) }
    }

    @Test
    fun `cancelExecution returns true for admin canceling another users execution`() {
        val alice = user()
        val admin = adminUser()
        val exec  = execution(alice, ExecutionStatus.RUNNING)
        every { helpers.findUser("admin") } returns admin
        every { executionLogRepository.findById(execId) } returns Optional.of(exec)
        every { executionLogRepository.findAllByParentExecutionIdOrderByStartedAtAsc(execId) } returns emptyList()
        every { executionLogRepository.save(exec) } returns exec

        assertTrue(service.cancelExecution(execId, "admin"))
    }

    @Test
    fun `cancelExecution returns false when execution is already finished`() {
        val alice = user()
        val exec  = execution(alice, ExecutionStatus.SUCCESS)
        every { helpers.findUser("alice") } returns alice
        every { executionLogRepository.findById(execId) } returns Optional.of(exec)

        assertFalse(service.cancelExecution(execId, "alice"))
    }

    @Test
    fun `cancelExecution returns false when user not found`() {
        every { helpers.findUser("ghost") } returns null

        assertFalse(service.cancelExecution(execId, "ghost"))
    }

    @Test
    fun `cancelExecution returns false when execution not found`() {
        val alice = user()
        every { helpers.findUser("alice") } returns alice
        every { executionLogRepository.findById(execId) } returns Optional.empty()

        assertFalse(service.cancelExecution(execId, "alice"))
    }

    @Test
    fun `cancelExecution returns false when non-admin cancels another users execution`() {
        val alice = user()
        val bob   = bob()
        val exec  = execution(alice, ExecutionStatus.RUNNING)  // owned by alice
        every { helpers.findUser("bob") } returns bob
        every { executionLogRepository.findById(execId) } returns Optional.of(exec)

        assertFalse(service.cancelExecution(execId, "bob"))
    }

    // triggerManualTask

    @Test
    fun `triggerManualTask returns Success with execution id for task owner`() {
        val alice = user()
        val t     = task(alice)
        val saved = execution(alice, ExecutionStatus.PENDING).also { it.id = execId }
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findByIdAndOwnerId(taskId, userId) } returns t
        every { executionLogRepository.save(any()) } returns saved

        val result = service.triggerManualTask(taskId, "alice")

        assertTrue(result is Success)
        assertEquals(execId, (result as Success).value)
    }

    @Test
    fun `triggerManualTask returns UserNotFound when user missing`() {
        every { helpers.findUser("ghost") } returns null

        val result = service.triggerManualTask(taskId, "ghost")

        assertTrue(result is Failure)
        assertEquals(ExecutionError.UserNotFound, (result as Failure).value)
    }

    @Test
    fun `triggerManualTask returns TaskNotFound when task not accessible to user`() {
        val alice = user()
        every { helpers.findUser("alice") } returns alice
        every { taskRepository.findByIdAndOwnerId(taskId, userId) } returns null

        val result = service.triggerManualTask(taskId, "alice")

        assertTrue(result is Failure)
        assertEquals(ExecutionError.TaskNotFound, (result as Failure).value)
    }

    @Test
    fun `triggerManualTask returns TaskNotFound for admin when task does not exist`() {
        val admin = adminUser()
        every { helpers.findUser("admin") } returns admin
        every { taskRepository.findById(taskId) } returns Optional.empty()

        val result = service.triggerManualTask(taskId, "admin")

        assertTrue(result is Failure)
        assertEquals(ExecutionError.TaskNotFound, (result as Failure).value)
    }
}
