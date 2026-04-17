package org.workflow.service.utils

// ── Auth ──────────────────────────────────────────────────────────────────────

/** All typed failure cases that [org.workflow.service.AuthService] can produce. */
sealed class AuthError {
    /** The username is already registered. */
    object UsernameAlreadyTaken : AuthError()

    /** The requested role does not exist in the database. */
    object RoleNotFound : AuthError()

    /** The supplied username/password pair is incorrect. */
    object InvalidCredentials : AuthError()

    /** No user with the given username exists. */
    object UserNotFound : AuthError()
}

// ── Workflow ──────────────────────────────────────────────────────────────────

/** All typed failure cases that [org.workflow.service.WorkflowService] can produce. */
sealed class WorkflowError {
    object WorkflowNotFound : WorkflowError()
    object UserNotFound : WorkflowError()
}

// ── Task ──────────────────────────────────────────────────────────────────────

/** All typed failure cases that [org.workflow.service.TaskService] can produce. */
sealed class TaskError {
    object TaskNotFound : TaskError()
    object WorkflowNotFound : TaskError()
    object UserNotFound : TaskError()
}

// ── User ──────────────────────────────────────────────────────────────────────

/** All typed failure cases that [org.workflow.service.UserService] can produce. */
sealed class UserError {
    /** The username is already registered. */
    object UsernameAlreadyTaken : UserError()

    /** The requested role does not exist in the database. */
    object RoleNotFound : UserError()
}

// ── Schedule ──────────────────────────────────────────────────────────────────

/** All typed failure cases that [org.workflow.service.ScheduleService] can produce. */
sealed class ScheduleError {
    object ScheduleNotFound : ScheduleError()
    object WorkflowNotFound : ScheduleError()
    object UserNotFound : ScheduleError()
    object InvalidCronExpression : ScheduleError()
}


