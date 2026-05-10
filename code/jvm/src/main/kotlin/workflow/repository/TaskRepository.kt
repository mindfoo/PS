package org.workflow.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.workflow.entity.Task
import java.util.UUID

@Repository
/** Data access operations for tasks with workflow and ownership filters. */
interface TaskRepository : JpaRepository<Task, UUID> {

    /** Tasks still carrying a direct workflow_id FK (legacy / newly created via workflow). */
    @Query("select t from Task t where t.workflow_id.id = :workflowId")
    fun findAllByWorkflowId(@Param("workflowId") workflowId: UUID): List<Task>

    @Query("select t from Task t where t.id = :taskId and t.workflow_id.id = :workflowId")
    fun findByIdAndWorkflowId(
        @Param("taskId") taskId: UUID,
        @Param("workflowId") workflowId: UUID
    ): Task?

    /** Ownership check via createdBy (standalone tasks) or via legacy workflow_id owner. */
    @Query("""
        select t from Task t
        where t.id = :taskId
          and (t.createdBy.id = :userId or t.workflow_id.created_by.id = :userId)
    """)
    fun findByIdAndOwnerId(
        @Param("taskId") taskId: UUID,
        @Param("userId") userId: UUID
    ): Task?

    /** All tasks created by a specific user (for the global task catalog). */
    @Query("select t from Task t where t.createdBy.id = :userId")
    fun findAllByCreatedById(@Param("userId") userId: UUID): List<Task>

    /** All tasks linked to a workflow via WorkflowTaskOrder. */
    @Query("select wto.task from WorkflowTaskOrder wto where wto.workflow.id = :workflowId")
    fun findAllLinkedToWorkflow(@Param("workflowId") workflowId: UUID): List<Task>

    /** Delete legacy tasks that are directly scoped to the workflow. */
    @Modifying
    @Query("delete from Task t where t.workflow_id.id = :workflowId")
    fun deleteAllByWorkflowId(@Param("workflowId") workflowId: UUID)
}
