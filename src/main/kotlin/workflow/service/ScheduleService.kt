package org.workflow.service

import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.workflow.dto.ScheduleCreateRequest
import org.workflow.dto.ScheduleResponse
import org.workflow.dto.ScheduleUpdateRequest
import org.workflow.entity.Schedule
import org.workflow.entity.User
import org.workflow.repository.ScheduleRepository
import org.workflow.repository.UserRepository
import org.workflow.repository.WorkflowRepository
import org.workflow.service.utils.ScheduleError
import org.workflow.utils.Either
import org.workflow.utils.failure
import org.workflow.utils.success
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@Service
/** Manages schedule CRUD, cron calculation and periodic dispatch orchestration. */
class ScheduleService(
    private val scheduleRepository: ScheduleRepository,
    private val workflowRepository: WorkflowRepository,
    private val userRepository: UserRepository,
    private val executionService: ExecutionService
) {

    fun list(authenticationName: String): Either<ScheduleError, List<ScheduleResponse>> {
        val user = findUser(authenticationName)
            ?: return failure(ScheduleError.UserNotFound)

        val schedules = if (isAdmin(user)) {
            scheduleRepository.findAll()
        } else {
            scheduleRepository.findAllByOwnerId(user.id!!)
        }
        return success(schedules.map { toResponse(it) })
    }

    fun getById(scheduleId: UUID, authenticationName: String): Either<ScheduleError, ScheduleResponse> {
        val user = findUser(authenticationName)
            ?: return failure(ScheduleError.UserNotFound)

        val schedule = if (isAdmin(user)) {
            scheduleRepository.findById(scheduleId).orElse(null)
        } else {
            scheduleRepository.findByIdAndOwnerId(scheduleId, user.id!!)
        } ?: return failure(ScheduleError.ScheduleNotFound)

        return success(toResponse(schedule))
    }

    @Transactional
    fun create(request: ScheduleCreateRequest, authenticationName: String): Either<ScheduleError, ScheduleResponse> {
        val user = findUser(authenticationName)
            ?: return failure(ScheduleError.UserNotFound)

        val workflow = if (isAdmin(user)) {
            workflowRepository.findById(request.workflowId).orElse(null)
        } else {
            workflowRepository.findByIdAndOwnerId(request.workflowId, user.id!!)
        } ?: return failure(ScheduleError.WorkflowNotFound)

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

        return success(toResponse(scheduleRepository.save(schedule)))
    }

    @Transactional
    fun update(
        scheduleId: UUID,
        request: ScheduleUpdateRequest,
        authenticationName: String
    ): Either<ScheduleError, ScheduleResponse> {
        val user = findUser(authenticationName)
            ?: return failure(ScheduleError.UserNotFound)

        val schedule = if (isAdmin(user)) {
            scheduleRepository.findById(scheduleId).orElse(null)
        } else {
            scheduleRepository.findByIdAndOwnerId(scheduleId, user.id!!)
        } ?: return failure(ScheduleError.ScheduleNotFound)

        val nextRun = computeNextRun(request.cronExpression, request.timezone)
            ?: return failure(ScheduleError.InvalidCronExpression)

        schedule.cronExpression = request.cronExpression
        schedule.timezone = request.timezone
        schedule.enabled = request.enabled
        schedule.nextRunAt = nextRun

        return success(toResponse(scheduleRepository.save(schedule)))
    }

    @Transactional
    fun delete(scheduleId: UUID, authenticationName: String): Either<ScheduleError, Unit> {
        val user = findUser(authenticationName)
            ?: return failure(ScheduleError.UserNotFound)

        val schedule = if (isAdmin(user)) {
            scheduleRepository.findById(scheduleId).orElse(null)
        } else {
            scheduleRepository.findByIdAndOwnerId(scheduleId, user.id!!)
        } ?: return failure(ScheduleError.ScheduleNotFound)

        scheduleRepository.delete(schedule)
        return success(Unit)
    }

    @Transactional
    fun dispatchDueSchedules() {
        val now = LocalDateTime.now()
        val dueSchedules = scheduleRepository.findDueSchedulesForUpdate(now)

        dueSchedules.forEach { schedule ->
            if (!schedule.enabled) return@forEach

            val executionId = executionService.createCronExecution(schedule.workflow, schedule.createdBy)
            executionService.runExecution(executionId)

            schedule.lastRunAt = now
            schedule.nextRunAt = computeNextRun(schedule.cronExpression, schedule.timezone)
                ?: now.plusMinutes(1)
            scheduleRepository.save(schedule)
        }
    }

    private fun computeNextRun(cronExpression: String, timezone: String): LocalDateTime? {
        val zone = ZoneId.of(timezone)
        val cron = CronExpression.parse(cronExpression)
        return cron.next(ZonedDateTime.now(zone))?.toLocalDateTime()
    }

    private fun findUser(username: String): User? =
        userRepository.findByUsername(username)

    private fun isAdmin(user: User): Boolean =
        user.role.name.equals("ADMIN", ignoreCase = true)

    private fun toResponse(schedule: Schedule): ScheduleResponse =
        ScheduleResponse(
            id = schedule.id,
            workflowId = schedule.workflow.id,
            workflowName = schedule.workflow.name,
            cronExpression = schedule.cronExpression,
            timezone = schedule.timezone,
            enabled = schedule.enabled,
            nextRunAt = schedule.nextRunAt,
            lastRunAt = schedule.lastRunAt
        )
}
