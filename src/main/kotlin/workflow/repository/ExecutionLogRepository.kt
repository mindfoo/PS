package org.workflow.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.workflow.entity.Execution
import java.util.UUID

@Repository
/** Data access operations for workflow execution logs. */
interface ExecutionLogRepository : JpaRepository<Execution, UUID> {
    fun findAllByWorkflowIdOrderByStartedAtDesc(workflowId: UUID): List<Execution>
}