package workflow.controller

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.security.core.Authentication
import org.springframework.web.multipart.MultipartFile
import org.workflow.controller.TaskController
import org.workflow.dto.ScriptInfoResponse
import org.workflow.dto.TaskCreateRequest
import org.workflow.dto.TaskResponse
import org.workflow.dto.TaskUpdateRequest
import org.workflow.service.ExecutionService
import org.workflow.service.TaskService
import org.workflow.service.utils.ExecutionError
import org.workflow.service.utils.TaskError
import org.workflow.utils.failure
import org.workflow.utils.success
import java.util.UUID

class TaskControllerTest {

    private lateinit var taskService: TaskService
    private lateinit var executionService: ExecutionService
    private lateinit var controller: TaskController
    private lateinit var auth: Authentication

    private val taskId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val wfId   = UUID.randomUUID()

    private fun taskResponse() = TaskResponse(taskId, "My Task", "SCRIPT", emptyMap(), wfId)
    private fun scriptInfo()   = ScriptInfoResponse(taskId, "script.py", 100L, null)

    @BeforeEach
    fun setup() {
        taskService      = mockk()
        executionService = mockk()
        controller       = TaskController(taskService, executionService)
        auth             = mockk(); every { auth.name } returns "alice"
    }

    // list (all)

    @Test
    fun `list with no workflowId returns 200 with task list`() {
        every { taskService.listAll("alice") } returns success(listOf(taskResponse()))
        assertEquals(200, controller.list(null, auth).statusCode.value())
    }

    @Test
    fun `list with no workflowId returns 404 when user not found`() {
        every { taskService.listAll("alice") } returns failure(TaskError.UserNotFound)
        assertEquals(404, controller.list(null, auth).statusCode.value())
    }

    // list (by workflow)

    @Test
    fun `list with workflowId returns 200 with ordered entries`() {
        every { taskService.listByWorkflow(wfId, "alice") } returns success(emptyList())
        assertEquals(200, controller.list(wfId, auth).statusCode.value())
    }

    @Test
    fun `list with workflowId returns 404 when workflow not found`() {
        every { taskService.listByWorkflow(wfId, "alice") } returns failure(TaskError.WorkflowNotFound)
        assertEquals(404, controller.list(wfId, auth).statusCode.value())
    }

    // getById

    @Test
    fun `getById returns 200 when task exists`() {
        every { taskService.getById(taskId, "alice") } returns success(taskResponse())
        assertEquals(200, controller.getById(taskId, auth).statusCode.value())
    }

    @Test
    fun `getById returns 404 when task not found`() {
        every { taskService.getById(taskId, "alice") } returns failure(TaskError.TaskNotFound)
        assertEquals(404, controller.getById(taskId, auth).statusCode.value())
    }

    // create

    @Test
    fun `create returns 201 when task is created`() {
        every { taskService.create(any(), "alice") } returns success(taskResponse())
        assertEquals(201, controller.create(TaskCreateRequest("My Task", "SCRIPT"), auth).statusCode.value())
    }

    @Test
    fun `create returns 404 when user not found`() {
        every { taskService.create(any(), "alice") } returns failure(TaskError.UserNotFound)
        assertEquals(404, controller.create(TaskCreateRequest("My Task", "SCRIPT"), auth).statusCode.value())
    }

    // update

    @Test
    fun `update returns 200 when task is updated`() {
        every { taskService.update(taskId, any(), "alice") } returns success(taskResponse())
        assertEquals(200, controller.update(taskId, TaskUpdateRequest("New", "SCRIPT"), auth).statusCode.value())
    }

    @Test
    fun `update returns 404 when task not found`() {
        every { taskService.update(taskId, any(), "alice") } returns failure(TaskError.TaskNotFound)
        assertEquals(404, controller.update(taskId, TaskUpdateRequest("New", "SCRIPT"), auth).statusCode.value())
    }

    // delete

    @Test
    fun `delete returns 204 on success`() {
        every { taskService.delete(taskId, "alice") } returns success(Unit)
        assertEquals(204, controller.delete(taskId, auth).statusCode.value())
    }

    @Test
    fun `delete returns 404 when task not found`() {
        every { taskService.delete(taskId, "alice") } returns failure(TaskError.TaskNotFound)
        assertEquals(404, controller.delete(taskId, auth).statusCode.value())
    }

    // runTask

    @Test
    fun `runTask returns 202 when execution is started`() {
        every { executionService.triggerManualTask(taskId, "alice") } returns success(UUID.randomUUID())
        assertEquals(202, controller.runTask(taskId, auth).statusCode.value())
    }

    @Test
    fun `runTask returns 404 when task not found`() {
        every { executionService.triggerManualTask(taskId, "alice") } returns failure(ExecutionError.TaskNotFound)
        assertEquals(404, controller.runTask(taskId, auth).statusCode.value())
    }

    // AccessDenied (403) coverage for private tasks

    @Test
    fun `getById returns 403 when task is private`() {
        every { taskService.getById(taskId, "alice") } returns failure(TaskError.AccessDenied)
        assertEquals(403, controller.getById(taskId, auth).statusCode.value())
    }

    @Test
    fun `update returns 403 when task is private`() {
        every { taskService.update(taskId, any(), "alice") } returns failure(TaskError.AccessDenied)
        assertEquals(403, controller.update(taskId, TaskUpdateRequest("X", "SCRIPT"), auth).statusCode.value())
    }

    @Test
    fun `delete returns 403 when task is private`() {
        every { taskService.delete(taskId, "alice") } returns failure(TaskError.AccessDenied)
        assertEquals(403, controller.delete(taskId, auth).statusCode.value())
    }

    // uploadScript

    @Test
    fun `uploadScript returns 201 when script is uploaded`() {
        val file = mockk<MultipartFile>()
        every { taskService.uploadScript(taskId, file, "alice") } returns success(scriptInfo())
        assertEquals(201, controller.uploadScript(taskId, file, auth).statusCode.value())
    }

    @Test
    fun `uploadScript returns 404 when task not found`() {
        val file = mockk<MultipartFile>()
        every { taskService.uploadScript(taskId, file, "alice") } returns failure(TaskError.TaskNotFound)
        assertEquals(404, controller.uploadScript(taskId, file, auth).statusCode.value())
    }

    @Test
    fun `uploadScript returns 403 when task is private`() {
        val file = mockk<MultipartFile>()
        every { taskService.uploadScript(taskId, file, "alice") } returns failure(TaskError.AccessDenied)
        assertEquals(403, controller.uploadScript(taskId, file, auth).statusCode.value())
    }

    @Test
    fun `uploadScript returns 400 when file type is invalid`() {
        val file = mockk<MultipartFile>()
        every { taskService.uploadScript(taskId, file, "alice") } returns failure(TaskError.InvalidFileType)
        assertEquals(400, controller.uploadScript(taskId, file, auth).statusCode.value())
    }

    @Test
    fun `uploadScript returns 413 when file is too large`() {
        val file = mockk<MultipartFile>()
        every { taskService.uploadScript(taskId, file, "alice") } returns failure(TaskError.FileTooLarge)
        assertEquals(413, controller.uploadScript(taskId, file, auth).statusCode.value())
    }

    @Test
    fun `uploadScript returns 404 when user not found`() {
        val file = mockk<MultipartFile>()
        every { taskService.uploadScript(taskId, file, "alice") } returns failure(TaskError.UserNotFound)
        assertEquals(404, controller.uploadScript(taskId, file, auth).statusCode.value())
    }

    // getScriptInfo

    @Test
    fun `getScriptInfo returns 200 with script metadata`() {
        every { taskService.getScriptInfo(taskId, "alice") } returns success(scriptInfo())
        assertEquals(200, controller.getScriptInfo(taskId, auth).statusCode.value())
    }

    @Test
    fun `getScriptInfo returns 404 when task not found`() {
        every { taskService.getScriptInfo(taskId, "alice") } returns failure(TaskError.TaskNotFound)
        assertEquals(404, controller.getScriptInfo(taskId, auth).statusCode.value())
    }

    @Test
    fun `getScriptInfo returns 404 when no script uploaded`() {
        every { taskService.getScriptInfo(taskId, "alice") } returns failure(TaskError.ScriptNotFound)
        assertEquals(404, controller.getScriptInfo(taskId, auth).statusCode.value())
    }

    @Test
    fun `getScriptInfo returns 403 when task is private`() {
        every { taskService.getScriptInfo(taskId, "alice") } returns failure(TaskError.AccessDenied)
        assertEquals(403, controller.getScriptInfo(taskId, auth).statusCode.value())
    }

    @Test
    fun `getScriptInfo returns 404 when user not found`() {
        every { taskService.getScriptInfo(taskId, "alice") } returns failure(TaskError.UserNotFound)
        assertEquals(404, controller.getScriptInfo(taskId, auth).statusCode.value())
    }
}