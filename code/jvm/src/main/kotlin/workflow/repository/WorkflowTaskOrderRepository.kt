package org.workflow.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.workflow.entity.WorkflowTaskOrder
import java.util.UUID

@Repository
/** Data access operations for task ordering metadata inside workflows. -> wto - workflowTaskOrder */
interface WorkflowTaskOrderRepository : JpaRepository<WorkflowTaskOrder, UUID> {

    @Query("select wto from WorkflowTaskOrder wto where wto.workflow.id = :workflowId order by wto.taskOrder asc")
    fun findAllByWorkflowIdOrderByTaskOrderAsc(@Param("workflowId") workflowId: UUID): List<WorkflowTaskOrder>

    @Query("select wto from WorkflowTaskOrder wto where wto.task.id = :taskId")
    fun findAllByTaskId(@Param("taskId") taskId: UUID): List<WorkflowTaskOrder>

    @Query("select wto from WorkflowTaskOrder wto where wto.workflow.id = :workflowId and wto.task.id = :taskId")
    fun findByWorkflowIdAndTaskId(
        @Param("workflowId") workflowId: UUID,
        @Param("taskId") taskId: UUID
    ): WorkflowTaskOrder?

    @Query("select wto from WorkflowTaskOrder wto where wto.workflow.id = :workflowId and wto.task.id = :taskId")
    fun findAllByWorkflowIdAndTaskId(
        @Param("workflowId") workflowId: UUID,
        @Param("taskId") taskId: UUID
    ): List<WorkflowTaskOrder>

    @Modifying
    @Query("delete from WorkflowTaskOrder wto where wto.workflow.id = :workflowId")
    fun deleteAllByWorkflowId(@Param("workflowId") workflowId: UUID)
}

