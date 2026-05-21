package org.workflow.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.workflow.entity.Execution
import java.util.UUID

/** Data access operations for workflow execution logs. */
@Repository
interface ExecutionLogRepository : JpaRepository<Execution, UUID> {

    /** Top-level workflow executions (not child task records), most recent first. */
    @Query("select e from Execution e where e.workflow.id = :workflowId and e.parentExecutionId is null order by e.startedAt desc")
    fun findTopLevelByWorkflowIdOrderByStartedAtDesc(workflowId: UUID): List<Execution>

    /** Child task-execution records that belong to a parent workflow execution. */
    fun findAllByParentExecutionIdOrderByStartedAtAsc(parentExecutionId: UUID): List<Execution>

    /** Most-recent top-level execution for a workflow (used for last-run status on dashboard). */
    @Query("select e from Execution e where e.workflow.id = :workflowId and e.parentExecutionId is null order by e.startedAt desc limit 1")
    fun findLatestByWorkflowId(workflowId: UUID): Execution?

    /** All executions for a given task (single-task runs), most recent first. */
    fun findAllByTaskIdAndParentExecutionIdIsNullOrderByStartedAtDesc(taskId: UUID): List<Execution>

    /** Delete all execution records (children first, then parents) for a workflow. */
    @Modifying
    @Query("delete from Execution e where e.workflow.id = :workflowId")
    fun deleteAllByWorkflowId(workflowId: UUID)
}