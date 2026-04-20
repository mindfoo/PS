package org.workflow.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.workflow.entity.Schedule
import java.time.LocalDateTime
import java.util.UUID

@Repository
/** Data access operations for workflow schedules and dispatch queries. */
interface ScheduleRepository : JpaRepository<Schedule, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Schedule s where s.enabled = true and s.nextRunAt <= :now")
    fun findDueSchedulesForUpdate(@Param("now") now: LocalDateTime): List<Schedule>

    @Query("select s from Schedule s where s.createdBy.id = :userId")
    fun findAllByOwnerId(@Param("userId") userId: UUID): List<Schedule>

    @Query("select s from Schedule s where s.id = :scheduleId and s.createdBy.id = :userId")
    fun findByIdAndOwnerId(
        @Param("scheduleId") scheduleId: UUID,
        @Param("userId") userId: UUID
    ): Schedule?
}

