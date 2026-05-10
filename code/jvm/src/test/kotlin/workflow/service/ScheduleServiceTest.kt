package workflow.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.workflow.dto.ScheduleCreateRequest
import org.workflow.dto.ScheduleUpdateRequest
import org.workflow.entity.Roles
import org.workflow.entity.Schedule
import org.workflow.entity.User
import org.workflow.entity.Workflow
import org.workflow.repository.ScheduleRepository
import org.workflow.repository.UserRepository
import org.workflow.repository.WorkflowRepository
import org.workflow.service.ExecutionService
import org.workflow.service.ScheduleService
import org.workflow.service.utils.ScheduleError
import org.workflow.utils.Failure
import org.workflow.utils.Success
import java.time.LocalDateTime
import java.util.UUID

class ScheduleServiceTest {

    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var workflowRepository: WorkflowRepository
    private lateinit var userRepository: UserRepository
    private lateinit var executionService: ExecutionService
    private lateinit var service: ScheduleService

    private val wfId         = UUID.randomUUID()
    private val scheduleId   = UUID.randomUUID()
    private val validCron    = "0 0 9 * * MON"  // Spring 6-field cron (sec min hr dom mon dow)
    private val invalidCron  = "INVALID_CRON"

    private fun role() = Roles(id = UUID.randomUUID(), name = "WRITER")
    private fun user() = User(id = UUID.randomUUID(), username = "alice", passwordValidation = "h", role = role())
    private fun workflow(owner: User) = Workflow(id = wfId, name = "WF", created_by = owner)
    private fun schedule(owner: User, wf: Workflow) = Schedule(
        id = scheduleId, workflow = wf, cronExpression = validCron, timezone = "UTC",
        enabled = true, nextRunAt = LocalDateTime.now().plusDays(1), createdBy = owner
    )

    @BeforeEach
    fun setup() {
        scheduleRepository = mockk()
        workflowRepository = mockk()
        userRepository     = mockk()
        executionService   = mockk()
        service = ScheduleService(scheduleRepository, workflowRepository, userRepository, executionService)
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    fun `list returns all schedules for admin`() {
        val admin = User(id = UUID.randomUUID(), username = "admin", passwordValidation = "h",
            role = Roles(id = UUID.randomUUID(), name = "ADMIN"))
        every { userRepository.findByUsername("admin") } returns admin
        every { scheduleRepository.findAll() } returns listOf(schedule(admin, workflow(admin)))

        val result = service.list("admin")

        assertTrue(result is Success)
        assertEquals(1, (result as Success).value.size)
    }

    @Test
    fun `list returns owned schedules for non-admin`() {
        val alice = user()
        every { userRepository.findByUsername("alice") } returns alice
        every { scheduleRepository.findAllByOwnerId(alice.id!!) } returns listOf(schedule(alice, workflow(alice)))

        val result = service.list("alice")

        assertTrue(result is Success)
    }

    @Test
    fun `list returns UserNotFound when user does not exist`() {
        every { userRepository.findByUsername("ghost") } returns null

        val result = service.list("ghost")

        assertTrue(result is Failure)
        assertEquals(ScheduleError.UserNotFound, (result as Failure).value)
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    fun `getById returns schedule when found`() {
        val alice = user()
        val s     = schedule(alice, workflow(alice))
        every { userRepository.findByUsername("alice") } returns alice
        every { scheduleRepository.findByIdAndOwnerId(scheduleId, alice.id!!) } returns s

        val result = service.getById(scheduleId, "alice")

        assertTrue(result is Success)
    }

    @Test
    fun `getById returns ScheduleNotFound when missing`() {
        val alice = user()
        every { userRepository.findByUsername("alice") } returns alice
        every { scheduleRepository.findByIdAndOwnerId(scheduleId, alice.id!!) } returns null

        val result = service.getById(scheduleId, "alice")

        assertTrue(result is Failure)
        assertEquals(ScheduleError.ScheduleNotFound, (result as Failure).value)
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    fun `create returns ScheduleResponse on success`() {
        val alice = user()
        val wf    = workflow(alice)
        val s     = schedule(alice, wf)
        every { userRepository.findByUsername("alice") } returns alice
        every { workflowRepository.findByIdAndOwnerId(wfId, alice.id!!) } returns wf
        every { scheduleRepository.save(any()) } returns s

        val result = service.create(ScheduleCreateRequest(wfId, validCron, "UTC"), "alice")

        assertTrue(result is Success)
        verify(exactly = 1) { scheduleRepository.save(any()) }
    }

    @Test
    fun `create returns InvalidCronExpression for bad cron`() {
        val alice = user()
        val wf    = workflow(alice)
        every { userRepository.findByUsername("alice") } returns alice
        every { workflowRepository.findByIdAndOwnerId(wfId, alice.id!!) } returns wf

        val result = service.create(ScheduleCreateRequest(wfId, invalidCron, "UTC"), "alice")

        assertTrue(result is Failure)
        assertEquals(ScheduleError.InvalidCronExpression, (result as Failure).value)
    }

    @Test
    fun `create returns WorkflowNotFound when workflow not found`() {
        val alice = user()
        every { userRepository.findByUsername("alice") } returns alice
        every { workflowRepository.findByIdAndOwnerId(wfId, alice.id!!) } returns null

        val result = service.create(ScheduleCreateRequest(wfId, validCron, "UTC"), "alice")

        assertTrue(result is Failure)
        assertEquals(ScheduleError.WorkflowNotFound, (result as Failure).value)
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    fun `update returns updated schedule on success`() {
        val alice = user()
        val s     = schedule(alice, workflow(alice))
        every { userRepository.findByUsername("alice") } returns alice
        every { scheduleRepository.findByIdAndOwnerId(scheduleId, alice.id!!) } returns s
        every { scheduleRepository.save(s) } returns s

        val result = service.update(scheduleId,
            ScheduleUpdateRequest(validCron, "UTC", true), "alice")

        assertTrue(result is Success)
    }

    @Test
    fun `update returns InvalidCronExpression for bad cron`() {
        val alice = user()
        val s     = schedule(alice, workflow(alice))
        every { userRepository.findByUsername("alice") } returns alice
        every { scheduleRepository.findByIdAndOwnerId(scheduleId, alice.id!!) } returns s

        val result = service.update(scheduleId,
            ScheduleUpdateRequest(invalidCron, "UTC", true), "alice")

        assertTrue(result is Failure)
        assertEquals(ScheduleError.InvalidCronExpression, (result as Failure).value)
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    fun `delete removes schedule on success`() {
        val alice = user()
        val s     = schedule(alice, workflow(alice))
        every { userRepository.findByUsername("alice") } returns alice
        every { scheduleRepository.findByIdAndOwnerId(scheduleId, alice.id!!) } returns s
        every { scheduleRepository.delete(s) } returns Unit

        val result = service.delete(scheduleId, "alice")

        assertTrue(result is Success)
        verify(exactly = 1) { scheduleRepository.delete(s) }
    }

    @Test
    fun `delete returns ScheduleNotFound when missing`() {
        val alice = user()
        every { userRepository.findByUsername("alice") } returns alice
        every { scheduleRepository.findByIdAndOwnerId(scheduleId, alice.id!!) } returns null

        val result = service.delete(scheduleId, "alice")

        assertTrue(result is Failure)
        assertEquals(ScheduleError.ScheduleNotFound, (result as Failure).value)
    }
}
