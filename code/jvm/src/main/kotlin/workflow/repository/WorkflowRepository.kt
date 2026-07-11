package org.workflow.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.workflow.entity.Workflow
import java.util.UUID

/** Data access operations for workflows with visibility filters. */
@Repository
interface WorkflowRepository : JpaRepository<Workflow, UUID> {

    /** Returns public workflows plus private workflows owned by the user. */
    @Query("select w from Workflow w where w.isPrivate = false or w.createdBy.id = :userId")
    fun findAllPublic(@Param("userId") userId: UUID): List<Workflow>
}