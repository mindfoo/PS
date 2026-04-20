package org.workflow.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.workflow.entity.Task
import java.util.UUID

@Repository
/** Data access operations for tasks with workflow and ownership filters. */
interface TaskRepository : JpaRepository<Task, UUID> {

    @Query("select t from Task t where t.workflow_id.id = :workflowId")
    fun findAllByWorkflowId(@Param("workflowId") workflowId: UUID): List<Task>

    @Query("select t from Task t where t.id = :taskId and t.workflow_id.id = :workflowId")
    fun findByIdAndWorkflowId(
        @Param("taskId") taskId: UUID,
        @Param("workflowId") workflowId: UUID
    ): Task?

    @Query("select t from Task t where t.id = :taskId and t.workflow_id.created_by.id = :userId")
    fun findByIdAndOwnerId(
        @Param("taskId") taskId: UUID,
        @Param("userId") userId: UUID
    ): Task?
}