package org.workflow.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.workflow.entity.Workflow
import java.util.UUID

/** Data access operations for workflows with ownership filters. */
@Repository
interface WorkflowRepository : JpaRepository<Workflow, UUID> {

    /** All workflows owned by a specific user — used for legacy admin queries. */
    @Query("select w from Workflow w where w.createdBy.id = :userId")
    fun findAllByOwnerId(@Param("userId") userId: UUID): List<Workflow>

    /** Ownership-scoped fetch — used in update/delete when the caller is not an admin. */
    @Query("select w from Workflow w where w.id = :workflowId and w.createdBy.id = :userId")
    fun findByIdAndOwnerId(
        @Param("workflowId") workflowId: UUID,
        @Param("userId") userId: UUID
    ): Workflow?

    /** Returns public workflows plus private workflows owned by the user. */
    @Query("select w from Workflow w where w.isPrivate = false or w.createdBy.id = :userId")
    fun findAllVisible(@Param("userId") userId: UUID): List<Workflow>
}