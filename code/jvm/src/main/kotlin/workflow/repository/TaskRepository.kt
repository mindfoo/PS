package org.workflow.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.workflow.entity.Task
import java.util.UUID

/** Data access operations for tasks with workflow and visibility filters. */
@Repository
interface TaskRepository : JpaRepository<Task, UUID> {

    /** Delete tasks that are directly scoped to the workflow. */
    @Modifying
    @Query("delete from Task t where t.workflow.id = :workflowId")
    fun deleteAllByWorkflowId(@Param("workflowId") workflowId: UUID)

    /** Returns public tasks plus private tasks owned by the user. */
    @Query("select t from Task t where t.isPrivate = false or t.createdBy.id = :userId")
    fun findAllPublic(@Param("userId") userId: UUID): List<Task>
}
