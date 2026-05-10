package workflow.controller

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.security.core.Authentication
import org.workflow.controller.ScheduleController
import org.workflow.dto.ScheduleCreateRequest
import org.workflow.dto.ScheduleResponse
import org.workflow.dto.ScheduleUpdateRequest
import org.workflow.service.ScheduleService
import org.workflow.service.utils.ScheduleError
import org.workflow.utils.failure
import org.workflow.utils.success
import java.time.LocalDateTime
import java.util.UUID

class ScheduleControllerTest {

    private lateinit var scheduleService: ScheduleService
    private lateinit var controller: ScheduleController
    private lateinit var auth: Authentication

    private val scheduleId = UUID.randomUUID()
    private val wfId       = UUID.randomUUID()

    private fun scheduleResponse() = ScheduleResponse(
        id = scheduleId, workflowId = wfId, workflowName = "WF",
        cronExpression = "0 9 * * 1", timezone = "UTC", enabled = true,
        nextRunAt = LocalDateTime.now().plusDays(1), lastRunAt = null
    )

    @BeforeEach
    fun setup() {
        scheduleService = mockk()
        controller      = ScheduleController(scheduleService)
        auth            = mockk(); every { auth.name } returns "alice"
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    fun `list returns 200 with schedule list`() {
        every { scheduleService.list("alice") } returns success(listOf(scheduleResponse()))
        assertEquals(200, controller.list(auth).statusCode.value())
    }

    @Test
    fun `list returns 404 when user not found`() {
        every { scheduleService.list("alice") } returns failure(ScheduleError.UserNotFound)
        assertEquals(404, controller.list(auth).statusCode.value())
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    fun `getById returns 200 when schedule exists`() {
        every { scheduleService.getById(scheduleId, "alice") } returns success(scheduleResponse())
        assertEquals(200, controller.getById(scheduleId, auth).statusCode.value())
    }

    @Test
    fun `getById returns 404 when schedule not found`() {
        every { scheduleService.getById(scheduleId, "alice") } returns failure(ScheduleError.ScheduleNotFound)
        assertEquals(404, controller.getById(scheduleId, auth).statusCode.value())
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    fun `create returns 201 when schedule is created`() {
        every { scheduleService.create(any(), "alice") } returns success(scheduleResponse())
        val request = ScheduleCreateRequest(workflowId = wfId, cronExpression = "0 9 * * 1", timezone = "UTC")
        assertEquals(201, controller.create(request, auth).statusCode.value())
    }

    @Test
    fun `create returns 400 when cron expression is invalid`() {
        every { scheduleService.create(any(), "alice") } returns failure(ScheduleError.InvalidCronExpression)
        val request = ScheduleCreateRequest(workflowId = wfId, cronExpression = "INVALID", timezone = "UTC")
        assertEquals(400, controller.create(request, auth).statusCode.value())
    }

    @Test
    fun `create returns 404 when workflow not found`() {
        every { scheduleService.create(any(), "alice") } returns failure(ScheduleError.WorkflowNotFound)
        val request = ScheduleCreateRequest(workflowId = wfId, cronExpression = "0 9 * * 1", timezone = "UTC")
        assertEquals(404, controller.create(request, auth).statusCode.value())
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    fun `update returns 200 when schedule is updated`() {
        every { scheduleService.update(scheduleId, any(), "alice") } returns success(scheduleResponse())
        val request = ScheduleUpdateRequest(cronExpression = "0 10 * * 1", timezone = "UTC", enabled = true)
        assertEquals(200, controller.update(scheduleId, request, auth).statusCode.value())
    }

    @Test
    fun `update returns 404 when schedule not found`() {
        every { scheduleService.update(scheduleId, any(), "alice") } returns failure(ScheduleError.ScheduleNotFound)
        val request = ScheduleUpdateRequest(cronExpression = "0 10 * * 1", timezone = "UTC", enabled = true)
        assertEquals(404, controller.update(scheduleId, request, auth).statusCode.value())
    }

    @Test
    fun `update returns 400 when cron expression is invalid`() {
        every { scheduleService.update(scheduleId, any(), "alice") } returns failure(ScheduleError.InvalidCronExpression)
        val request = ScheduleUpdateRequest(cronExpression = "INVALID", timezone = "UTC", enabled = true)
        assertEquals(400, controller.update(scheduleId, request, auth).statusCode.value())
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    fun `delete returns 204 on success`() {
        every { scheduleService.delete(scheduleId, "alice") } returns success(Unit)
        assertEquals(204, controller.delete(scheduleId, auth).statusCode.value())
    }

    @Test
    fun `delete returns 404 when schedule not found`() {
        every { scheduleService.delete(scheduleId, "alice") } returns failure(ScheduleError.ScheduleNotFound)
        assertEquals(404, controller.delete(scheduleId, auth).statusCode.value())
    }
}
