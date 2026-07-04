package workflow.controller

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.security.core.Authentication
import org.workflow.controller.WorkflowController
import org.workflow.dto.ExecutionSummaryResponse
import org.workflow.dto.RetryPolicyUpdateRequest
import org.workflow.dto.TaskOrderItem
import org.workflow.dto.TaskReorderRequest
import org.workflow.dto.WorkflowCreateRequest
import org.workflow.dto.WorkflowResponse
import org.workflow.dto.WorkflowUpdateRequest
import org.workflow.service.ExecutionService
import java.time.LocalDateTime
import org.workflow.service.TaskService
import org.workflow.service.WorkflowService
import org.workflow.service.utils.WorkflowError
import org.workflow.service.utils.TaskError
import org.workflow.service.utils.ExecutionError
import org.workflow.utils.failure
import org.workflow.utils.success
import java.util.UUID

class WorkflowControllerTest {

    private lateinit var workflowService: WorkflowService
    private lateinit var executionService: ExecutionService
    private lateinit var taskService: TaskService
    private lateinit var controller: WorkflowController
    private lateinit var auth: Authentication

    private val userId  = UUID.randomUUID()
    private val wfId    = UUID.randomUUID()
    private val taskId  = UUID.randomUUID()
    private val execId  = UUID.randomUUID()

    private fun workflowResponse() = WorkflowResponse(wfId, "My WF", userId, "alice", null)

    @BeforeEach
    fun setup() {
        workflowService  = mockk()
        executionService = mockk()
        taskService      = mockk()
        controller       = WorkflowController(workflowService, executionService, taskService)
        auth             = mockk(); every { auth.name } returns "alice"
    }

    // list

    @Test
    fun `list returns 200 with workflows`() {
        every { workflowService.list("alice") } returns success(listOf(workflowResponse()))
        assertEquals(200, controller.list(auth).statusCode.value())
    }

    @Test
    fun `list returns 404 when user not found`() {
        every { workflowService.list("alice") } returns failure(WorkflowError.UserNotFound)
        assertEquals(404, controller.list(auth).statusCode.value())
    }

    // getById

    @Test
    fun `getById returns 200 when workflow exists`() {
        every { workflowService.getById(wfId, "alice") } returns success(workflowResponse())
        assertEquals(200, controller.getById(wfId, auth).statusCode.value())
    }

    @Test
    fun `getById returns 404 when workflow not found`() {
        every { workflowService.getById(wfId, "alice") } returns failure(WorkflowError.WorkflowNotFound)
        assertEquals(404, controller.getById(wfId, auth).statusCode.value())
    }

    // create

    @Test
    fun `create returns 201 when workflow is created`() {
        every { workflowService.create(any(), "alice") } returns success(workflowResponse())
        assertEquals(201, controller.create(WorkflowCreateRequest("My WF"), auth).statusCode.value())
    }

    @Test
    fun `create returns 404 when user not found`() {
        every { workflowService.create(any(), "alice") } returns failure(WorkflowError.UserNotFound)
        assertEquals(404, controller.create(WorkflowCreateRequest("My WF"), auth).statusCode.value())
    }

    // update

    @Test
    fun `update returns 200 when workflow is updated`() {
        every { workflowService.update(wfId, any(), "alice") } returns success(workflowResponse())
        assertEquals(200, controller.update(wfId, WorkflowUpdateRequest("New Name"), auth).statusCode.value())
    }

    @Test
    fun `update returns 404 when workflow not found`() {
        every { workflowService.update(wfId, any(), "alice") } returns failure(WorkflowError.WorkflowNotFound)
        assertEquals(404, controller.update(wfId, WorkflowUpdateRequest("New Name"), auth).statusCode.value())
    }

    // delete

    @Test
    fun `delete returns 204 on success`() {
        every { workflowService.delete(wfId, "alice") } returns success(Unit)
        assertEquals(204, controller.delete(wfId, auth).statusCode.value())
    }

    @Test
    fun `delete returns 404 when workflow not found`() {
        every { workflowService.delete(wfId, "alice") } returns failure(WorkflowError.WorkflowNotFound)
        assertEquals(404, controller.delete(wfId, auth).statusCode.value())
    }

    // runNow

    @Test
    fun `runNow returns 202 with execution id`() {
        every { executionService.triggerManualWorkflow(wfId, "alice") } returns success(execId)
        val response = controller.runNow(wfId, auth)
        assertEquals(202, response.statusCode.value())
    }

    @Test
    fun `runNow returns 404 when user not found`() {
        every { executionService.triggerManualWorkflow(wfId, "alice") } returns failure(ExecutionError.UserNotFound)
        assertEquals(404, controller.runNow(wfId, auth).statusCode.value())
    }

    @Test
    fun `runNow returns 404 when workflow not found`() {
        every { executionService.triggerManualWorkflow(wfId, "alice") } returns failure(ExecutionError.WorkflowNotFound)
        assertEquals(404, controller.runNow(wfId, auth).statusCode.value())
    }

    // listExecutions

    @Test
    fun `listExecutions returns 200 with execution list`() {
        every { workflowService.listExecutions(wfId, "alice") } returns success(emptyList<ExecutionSummaryResponse>())
        assertEquals(200, controller.listExecutions(wfId, auth).statusCode.value())
    }

    @Test
    fun `listExecutions returns 404 when workflow not found`() {
        every { workflowService.listExecutions(wfId, "alice") } returns failure(WorkflowError.WorkflowNotFound)
        assertEquals(404, controller.listExecutions(wfId, auth).statusCode.value())
    }

    // linkTask / unlinkTask

    @Test
    fun `linkTask returns 204 on success`() {
        every { taskService.linkToWorkflow(taskId, wfId, "alice") } returns success(Unit)
        assertEquals(204, controller.linkTask(wfId, taskId, auth).statusCode.value())
    }

    @Test
    fun `linkTask returns 409 when already linked`() {
        every { taskService.linkToWorkflow(taskId, wfId, "alice") } returns failure(TaskError.AlreadyLinked)
        assertEquals(409, controller.linkTask(wfId, taskId, auth).statusCode.value())
    }

    @Test
    fun `unlinkTask returns 204 on success`() {
        every { taskService.unlinkFromWorkflow(taskId, wfId, "alice") } returns success(Unit)
        assertEquals(204, controller.unlinkTask(wfId, taskId, auth).statusCode.value())
    }

    @Test
    fun `unlinkTask returns 404 when link not found`() {
        every { taskService.unlinkFromWorkflow(taskId, wfId, "alice") } returns failure(TaskError.NotLinked)
        assertEquals(404, controller.unlinkTask(wfId, taskId, auth).statusCode.value())
    }

    // cancelExecution

    @Test
    fun `cancelExecution returns 204 when execution is canceled`() {
        every { executionService.cancelExecution(execId, "alice") } returns success(Unit)
        assertEquals(204, controller.cancelExecution(execId, auth).statusCode.value())
    }

    @Test
    fun `cancelExecution returns 409 when execution is not cancelable`() {
        every { executionService.cancelExecution(execId, "alice") } returns failure(ExecutionError.NotCancelable)
        assertEquals(409, controller.cancelExecution(execId, auth).statusCode.value())
    }

    // reorderTasks

    @Test
    fun `reorderTasks returns 204 on success`() {
        val request = TaskReorderRequest(items = listOf(TaskOrderItem(orderId = taskId, taskOrder = 2)))
        every { workflowService.reorderTasks(wfId, request, "alice") } returns success(Unit)
        assertEquals(204, controller.reorderTasks(wfId, request, auth).statusCode.value())
    }

    @Test
    fun `reorderTasks returns 404 when workflow not found`() {
        val request = TaskReorderRequest(items = emptyList())
        every { workflowService.reorderTasks(wfId, request, "alice") } returns failure(WorkflowError.WorkflowNotFound)
        assertEquals(404, controller.reorderTasks(wfId, request, auth).statusCode.value())
    }

    // updateRetryPolicy

    @Test
    fun `updateRetryPolicy returns 204 on success`() {
        val request = RetryPolicyUpdateRequest(retryPolicy = 3)
        every { workflowService.updateRetryPolicy(wfId, taskId, request, "alice") } returns success(Unit)
        assertEquals(204, controller.updateRetryPolicy(wfId, taskId, request, auth).statusCode.value())
    }

    @Test
    fun `updateRetryPolicy returns 404 when not found`() {
        val request = RetryPolicyUpdateRequest(retryPolicy = 3)
        every { workflowService.updateRetryPolicy(wfId, taskId, request, "alice") } returns failure(WorkflowError.WorkflowNotFound)
        assertEquals(404, controller.updateRetryPolicy(wfId, taskId, request, auth).statusCode.value())
    }

    // getExecution

    @Test
    fun `getExecution returns 200 with execution summary`() {
        every { workflowService.getExecution(execId, "alice") } returns success(
            ExecutionSummaryResponse(execId, "MANUAL", "WORKFLOW", "SUCCESS",
                LocalDateTime.now(), null, "alice", 0, null, null)
        )
        assertEquals(200, controller.getExecution(execId, auth).statusCode.value())
    }

    @Test
    fun `getExecution returns 404 when execution not found`() {
        every { workflowService.getExecution(execId, "alice") } returns failure(WorkflowError.WorkflowNotFound)
        assertEquals(404, controller.getExecution(execId, auth).statusCode.value())
    }

    // AccessDenied (403) coverage for private resources

    @Test
    fun `getById returns 403 when resource is private and user has no access`() {
        every { workflowService.getById(wfId, "alice") } returns failure(WorkflowError.AccessDenied)
        assertEquals(403, controller.getById(wfId, auth).statusCode.value())
    }

    @Test
    fun `update returns 403 when workflow is private`() {
        every { workflowService.update(wfId, any(), "alice") } returns failure(WorkflowError.AccessDenied)
        assertEquals(403, controller.update(wfId, WorkflowUpdateRequest("X"), auth).statusCode.value())
    }

    @Test
    fun `delete returns 403 when workflow is private`() {
        every { workflowService.delete(wfId, "alice") } returns failure(WorkflowError.AccessDenied)
        assertEquals(403, controller.delete(wfId, auth).statusCode.value())
    }

    @Test
    fun `listExecutions returns 403 when workflow is private`() {
        every { workflowService.listExecutions(wfId, "alice") } returns failure(WorkflowError.AccessDenied)
        assertEquals(403, controller.listExecutions(wfId, auth).statusCode.value())
    }

    @Test
    fun `linkTask returns 403 when workflow is private`() {
        every { taskService.linkToWorkflow(taskId, wfId, "alice") } returns failure(TaskError.AccessDenied)
        assertEquals(403, controller.linkTask(wfId, taskId, auth).statusCode.value())
    }

    @Test
    fun `unlinkTask returns 403 when workflow is private`() {
        every { taskService.unlinkFromWorkflow(taskId, wfId, "alice") } returns failure(TaskError.AccessDenied)
        assertEquals(403, controller.unlinkTask(wfId, taskId, auth).statusCode.value())
    }

    @Test
    fun `reorderTasks returns 403 when workflow is private`() {
        val request = TaskReorderRequest(items = emptyList())
        every { workflowService.reorderTasks(wfId, request, "alice") } returns failure(WorkflowError.AccessDenied)
        assertEquals(403, controller.reorderTasks(wfId, request, auth).statusCode.value())
    }

    @Test
    fun `updateRetryPolicy returns 403 when workflow is private`() {
        val request = RetryPolicyUpdateRequest(retryPolicy = 2)
        every { workflowService.updateRetryPolicy(wfId, taskId, request, "alice") } returns failure(WorkflowError.AccessDenied)
        assertEquals(403, controller.updateRetryPolicy(wfId, taskId, request, auth).statusCode.value())
    }

    @Test
    fun `getExecution returns 403 when execution is private`() {
        every { workflowService.getExecution(execId, "alice") } returns failure(WorkflowError.AccessDenied)
        assertEquals(403, controller.getExecution(execId, auth).statusCode.value())
    }
}