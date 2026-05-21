package org.workflow.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.workflow.entity.Task
import java.util.UUID

/** Data access operations for tasks with workflow and ownership filters. */
@Repository
interface TaskRepository : JpaRepository<Task, UUID> {

    /** Tasks scoped to a workflow via the direct workflow FK. */
    @Query("select t from Task t where t.workflow.id = :workflowId")
    fun findAllByWorkflowId(@Param("workflowId") workflowId: UUID): List<Task>

    @Query("select t from Task t where t.id = :taskId and t.workflow.id = :workflowId")
    fun findByIdAndWorkflowId(
        @Param("taskId") taskId: UUID,
        @Param("workflowId") workflowId: UUID
    ): Task?

    /** Ownership check via createdBy (standalone tasks) or via workflow owner (workflow-scoped tasks). */
    @Query("""
        select t from Task t
        where t.id = :taskId
          and (t.createdBy.id = :userId or t.workflow.createdBy.id = :userId)
    """)
    fun findByIdAndOwnerId(
        @Param("taskId") taskId: UUID,
        @Param("userId") userId: UUID
    ): Task?

    /** All tasks created by a specific user — used in admin list path; non-admins use findAllVisible. */
    @Query("select t from Task t where t.createdBy.id = :userId")
    fun findAllByCreatedById(@Param("userId") userId: UUID): List<Task>

    /** All tasks linked to a workflow via WorkflowTaskOrder. */
    @Query("select wto.task from WorkflowTaskOrder wto where wto.workflow.id = :workflowId")
    fun findAllLinkedToWorkflow(@Param("workflowId") workflowId: UUID): List<Task>

    /** Delete tasks that are directly scoped to the workflow. */
    @Modifying
    @Query("delete from Task t where t.workflow.id = :workflowId")
    fun deleteAllByWorkflowId(@Param("workflowId") workflowId: UUID)

    /** Returns public tasks plus private tasks owned by the user. */
    @Query("select t from Task t where t.isPrivate = false or t.createdBy.id = :userId")
    fun findAllVisible(@Param("userId") userId: UUID): List<Task>
}
