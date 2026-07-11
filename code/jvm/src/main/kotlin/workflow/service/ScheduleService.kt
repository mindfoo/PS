package org.workflow.service

import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.workflow.dto.ScheduleCreateRequest
import org.workflow.dto.ScheduleResponse
import org.workflow.dto.ScheduleUpdateRequest
import org.workflow.entity.Schedule
import org.workflow.entity.User
import org.workflow.entity.Workflow
import org.workflow.repository.ScheduleRepository
import org.workflow.repository.WorkflowRepository
import org.workflow.service.utils.ScheduleError
import org.workflow.utils.Either
import org.workflow.utils.failure
import org.workflow.utils.success
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

/** Manages schedule CRUD, cron calculation and periodic dispatch orchestration. */
@Service
class ScheduleService(
    private val scheduleRepository: ScheduleRepository,
    private val workflowRepository: WorkflowRepository,
    private val executionService: ExecutionService,
    private val helpers: ServiceHelpers
) {

    @Transactional(readOnly = true)
    fun list(authenticationName: String): Either<ScheduleError, List<ScheduleResponse>> {
        val user = findUser(authenticationName)
            ?: return failure(ScheduleError.UserNotFound)

        val userId = user.id ?: return failure(ScheduleError.UserNotFound)
        val schedules = if (isAdmin(user)) {
            scheduleRepository.findAll()
        } else {
            scheduleRepository.findAllPublic(userId)
        }
        return success(schedules.map { it.toResponse() })
    }

    @Transactional(readOnly = true)
    fun getById(scheduleId: UUID, authenticationName: String): Either<ScheduleError, ScheduleResponse> {
        val user = findUser(authenticationName)
            ?: return failure(ScheduleError.UserNotFound)

        val schedule = findPublicSchedule(scheduleId, user)
            ?: return failure(ScheduleError.ScheduleNotFound)

        return success(schedule.toResponse())
    }

    @Transactional
    fun create(request: ScheduleCreateRequest, authenticationName: String): Either<ScheduleError, ScheduleResponse> {
        val user = findUser(authenticationName)
            ?: return failure(ScheduleError.UserNotFound)

        val workflow = findPublicWorkflow(request.workflowId, user)
            ?: return failure(ScheduleError.WorkflowNotFound)

        val nextRun = computeNextRun(request.cronExpression, request.timezone)
            ?: return failure(ScheduleError.InvalidCronExpression)

        val schedule = Schedule(
            workflow = workflow,
            cronExpression = request.cronExpression,
            timezone = request.timezone,
            enabled = request.enabled,
            nextRunAt = nextRun,
            createdBy = user
        )

        return success(scheduleRepository.save(schedule).toResponse())
    }

    @Transactional
    fun update(
        scheduleId: UUID,
        request: ScheduleUpdateRequest,
        authenticationName: String
    ): Either<ScheduleError, ScheduleResponse> {
        val user = findUser(authenticationName)
            ?: return failure(ScheduleError.UserNotFound)

        val schedule = findPublicSchedule(scheduleId, user)
            ?: return failure(ScheduleError.ScheduleNotFound)

        val nextRun = computeNextRun(request.cronExpression, request.timezone)
            ?: return failure(ScheduleError.InvalidCronExpression)

        schedule.cronExpression = request.cronExpression
        schedule.timezone = request.timezone
        schedule.enabled = request.enabled
        schedule.nextRunAt = nextRun

        return success(scheduleRepository.save(schedule).toResponse())
    }

    @Transactional
    fun delete(scheduleId: UUID, authenticationName: String): Either<ScheduleError, Unit> {
        val user = findUser(authenticationName)
            ?: return failure(ScheduleError.UserNotFound)

        val schedule = findPublicSchedule(scheduleId, user)
            ?: return failure(ScheduleError.ScheduleNotFound)

        scheduleRepository.delete(schedule)
        return success(Unit)
    }

    @Transactional
    fun dispatchDueSchedules() {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val dueSchedules = scheduleRepository.findDueSchedulesForUpdate(now)

        dueSchedules.forEach { schedule ->
            if (!schedule.enabled) return@forEach

            val executionId = executionService.createCronExecution(schedule.workflow, schedule.createdBy)
            executionService.runExecutionAfterCommit(executionId)

            schedule.lastRunAt = now
            schedule.nextRunAt = computeNextRun(schedule.cronExpression, schedule.timezone)
                ?: now.plusMinutes(1)
            scheduleRepository.save(schedule)
        }
    }

    private fun computeNextRun(cronExpression: String, timezone: String): LocalDateTime? {
        return try {
            val zone = ZoneId.of(timezone)
            val cron = CronExpression.parse(cronExpression)
            val nextInZone = cron.next(ZonedDateTime.now(zone))
            nextInZone?.withZoneSameInstant(ZoneOffset.UTC)?.toLocalDateTime()
        } catch (_: Exception) {
            null
        }
    }

    private fun findUser(username: String) = helpers.findUser(username)
    private fun isAdmin(user: User) = helpers.isAdmin(user)

    /** Schedules follow their workflow's visibility: public workflows plus the user's own private ones. */
    private fun findPublicSchedule(scheduleId: UUID, user: User): Schedule? {
        val schedule = scheduleRepository.findByIdOrNull(scheduleId) ?: return null
        val workflow = schedule.workflow
        return if (isPublic(workflow.isPrivate, workflow.createdBy.id, isAdmin(user), user.id)) schedule else null
    }


    private fun findPublicWorkflow(workflowId: UUID, user: User): Workflow? {
        val workflow = workflowRepository.findByIdOrNull(workflowId) ?: return null
        return if (isPublic(workflow.isPrivate, workflow.createdBy.id, isAdmin(user), user.id)) workflow else null
    }

    private fun Schedule.toResponse(): ScheduleResponse =
        ScheduleResponse(
            id = id,
            workflowId = workflow.id,
            workflowName = workflow.name,
            cronExpression = cronExpression,
            timezone = timezone,
            enabled = enabled,
            nextRunAt = nextRunAt,
            lastRunAt = lastRunAt
        )
}
