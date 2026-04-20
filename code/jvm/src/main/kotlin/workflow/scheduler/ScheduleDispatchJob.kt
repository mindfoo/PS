package org.workflow.scheduler

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.workflow.service.ScheduleService

@Component
/** Periodic scheduler job that dispatches due workflow schedules. */
class ScheduleDispatchJob(
    private val scheduleService: ScheduleService
) {

    @Scheduled(fixedDelayString = "\${scheduler.dispatch.fixed-delay-ms:10000}")
    fun dispatchDueSchedules() {
        scheduleService.dispatchDueSchedules()
    }
}
