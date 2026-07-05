package org.workflow.utils

import org.springframework.http.ResponseEntity
import org.workflow.service.utils.ExecutionError
import org.workflow.service.utils.ScheduleError
import org.workflow.service.utils.TaskError
import org.workflow.service.utils.WorkflowError

/**
 * Maps each service-layer error type to its RFC 7807 Problem response.
 * Shared by every controller so each failure case has exactly one HTTP mapping.
 */

fun WorkflowError.toResponse(): ResponseEntity<Any> = when (this) {
    WorkflowError.UserNotFound      -> Problem.response(404, Problem.userNotFound)
    WorkflowError.WorkflowNotFound  -> Problem.response(404, Problem.workflowNotFound)
    WorkflowError.AccessDenied      -> Problem.response(403, Problem.accessDenied)
    WorkflowError.TaskNotLinked     -> Problem.response(404, Problem.taskNotLinked)
    WorkflowError.ExecutionNotFound -> Problem.response(404, Problem.executionNotFound)
}

fun TaskError.toResponse(): ResponseEntity<Any> = when (this) {
    TaskError.UserNotFound     -> Problem.response(404, Problem.userNotFound)
    TaskError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
    TaskError.TaskNotFound     -> Problem.response(404, Problem.taskNotFound)
    TaskError.AlreadyLinked    -> Problem.response(409, Problem.taskAlreadyLinked)
    TaskError.NotLinked        -> Problem.response(404, Problem.taskNotLinked)
    TaskError.AccessDenied     -> Problem.response(403, Problem.accessDenied)
}

fun ScheduleError.toResponse(): ResponseEntity<Any> = when (this) {
    ScheduleError.UserNotFound          -> Problem.response(404, Problem.userNotFound)
    ScheduleError.ScheduleNotFound      -> Problem.response(404, Problem.scheduleNotFound)
    ScheduleError.WorkflowNotFound      -> Problem.response(404, Problem.workflowNotFound)
    ScheduleError.InvalidCronExpression -> Problem.response(400, Problem.invalidCronExpression)
}

fun ExecutionError.toResponse(): ResponseEntity<Any> = when (this) {
    ExecutionError.UserNotFound     -> Problem.response(404, Problem.userNotFound)
    ExecutionError.WorkflowNotFound -> Problem.response(404, Problem.workflowNotFound)
    ExecutionError.TaskNotFound     -> Problem.response(404, Problem.taskNotFound)
    ExecutionError.NotCancelable    -> Problem.response(409, Problem.notCancelable)
}
