package org.workflow.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.workflow.entity.Alert
import java.util.UUID

@Repository
interface AlertRepository : JpaRepository<Alert, UUID> {

    /** Delete all alerts whose execution belongs to the given workflow. */
    @Modifying
    @Query("delete from Alert a where a.execution.workflow.id = :workflowId")
    fun deleteAllByWorkflowId(@Param("workflowId") workflowId: UUID)
}
