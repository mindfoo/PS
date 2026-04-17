package org.workflow.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.workflow.entity.Workflow
import java.util.UUID

@Repository
/** Data access operations for workflows with ownership filters. */
interface WorkflowRepository : JpaRepository<Workflow, UUID> {

    @Query("select w from Workflow w where w.created_by.id = :userId")
    fun findAllByOwnerId(@Param("userId") userId: UUID): List<Workflow>

    @Query("select w from Workflow w where w.id = :workflowId and w.created_by.id = :userId")
    fun findByIdAndOwnerId(
        @Param("workflowId") workflowId: UUID,
        @Param("userId") userId: UUID
    ): Workflow?
}