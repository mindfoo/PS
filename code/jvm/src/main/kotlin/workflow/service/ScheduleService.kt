package org.workflow.service

import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.CompletableFuture
import org.workflow.dto.ScheduleCreateRequest
import org.workflow.dto.ScheduleResponse
import org.workflow.dto.ScheduleUpdateRequest
import org.workflow.entity.Schedule
import org.workflow.entity.User
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
            scheduleRepository.findAllByOwnerId(userId)
        }
        return success(schedules.map { it.toResponse() })
    }

    @Transactional(readOnly = true)
    fun getById(scheduleId: UUID, authenticationName: String): Either<ScheduleError, ScheduleResponse> {
        val user = findUser(authenticationName)
            ?: return failure(ScheduleError.UserNotFound)

        val schedule = findOwnedSchedule(scheduleId, user)
            ?: return failure(ScheduleError.ScheduleNotFound)

        return success(schedule.toResponse())
    }

    @Transactional
    fun create(request: ScheduleCreateRequest, authenticationName: String): Either<ScheduleError, ScheduleResponse> {
        val user = findUser(authenticationName)
            ?: return failure(ScheduleError.UserNotFound)

        val userId = user.id ?: return failure(ScheduleError.UserNotFound)
        val workflow = if (isAdmin(user)) {
            workflowRepository.findById(request.workflowId).orElse(null)
        } else {
            workflowRepository.findByIdAndOwnerId(request.workflowId, userId)
        } ?: return failure(ScheduleError.WorkflowNotFound)

        val nextRun = computeNextRun(request.cronExpression, request.timezone)
            ?: return failure(ScheduleError.InvalidCronExpression)

        val schedule = Schedule(
            workflow = workflow,
            cronExpression = request.cronExpression,
            timezone = request.timezone,
            enabled = request.enabled,
            nextRunAt = nextRun,
            createdBy = user,
            description = request.description
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

        val schedule = findOwnedSchedule(scheduleId, user)
            ?: return failure(ScheduleError.ScheduleNotFound)

        val nextRun = computeNextRun(request.cronExpression, request.timezone)
            ?: return failure(ScheduleError.InvalidCronExpression)

        schedule.cronExpression = request.cronExpression
        schedule.timezone = request.timezone
        schedule.enabled = request.enabled
        schedule.nextRunAt = nextRun
        schedule.description = request.description

        return success(scheduleRepository.save(schedule).toResponse())
    }

    @Transactional
    fun delete(scheduleId: UUID, authenticationName: String): Either<ScheduleError, Unit> {
        val user = findUser(authenticationName)
            ?: return failure(ScheduleError.UserNotFound)

        val schedule = findOwnedSchedule(scheduleId, user)
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

            // Dispatch AFTER this transaction commits: createCronExecution saves the execution
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    CompletableFuture.runAsync { executionService.runExecution(executionId) }
                }
            })

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

    /** Admins can access any schedule by ID; other users only their own. */
    private fun findOwnedSchedule(scheduleId: UUID, user: User): Schedule? {
        if (isAdmin(user)) return scheduleRepository.findById(scheduleId).orElse(null)
        val userId = user.id ?: return null
        return scheduleRepository.findByIdAndOwnerId(scheduleId, userId)
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
            lastRunAt = lastRunAt,
            description = description
        )
}
