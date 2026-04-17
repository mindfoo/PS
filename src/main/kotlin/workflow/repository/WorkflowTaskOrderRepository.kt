package org.workflow.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.workflow.entity.WorkflowTaskOrder
import java.util.UUID

@Repository
/** Data access operations for task ordering metadata inside workflows. */
interface WorkflowTaskOrderRepository : JpaRepository<WorkflowTaskOrder, UUID> {

    @Query("select wto from WorkflowTaskOrder wto where wto.workflow.id = :workflowId order by wto.taskOrder asc")
    fun findAllByWorkflowIdOrderByTaskOrderAsc(@Param("workflowId") workflowId: UUID): List<WorkflowTaskOrder>
}

