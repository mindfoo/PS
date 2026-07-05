package org.workflow.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
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

    /**
     * Returns (taskId, status) pairs for all child task-level executions of a workflow execution.
     * Used by [ExecutionEventService] to build the catch-up event payload on SSE subscribe.
     */
    @Query("select e.task.id, e.status from Execution e where e.parentExecutionId = :parentId and e.task is not null")
    fun findTaskStatusesByParentId(@Param("parentId") parentId: UUID): List<Array<Any>>

    /** Delete all execution records (children first, then parents) for a workflow. */
    @Modifying
    @Query("delete from Execution e where e.workflow.id = :workflowId")
    fun deleteAllByWorkflowId(workflowId: UUID)

    /**
     * Atomically sets [Execution.cancelRequested] to true only while the execution is still
     * active. Returns the number of rows updated — 0 means the execution already reached a
     * terminal state and the cancel signal was ignored.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Execution e SET e.cancelRequested = true WHERE e.id = :id AND e.status IN ('PENDING', 'RUNNING')")
    fun requestCancellation(@Param("id") id: UUID): Int

    /** Lightweight check used by [ExecutionService] between stages to honour a pending cancel. */
    @Query("SELECT e.cancelRequested FROM Execution e WHERE e.id = :id")
    fun isCancelRequested(@Param("id") id: UUID): Boolean
}