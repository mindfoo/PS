package org.workflow.service.utils

/** Auth */

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

    /** The password does not meet the minimum security requirements. */
    object InsecurePassword : AuthError()
}

/** Workflow */

/** All typed failure cases that [org.workflow.service.WorkflowService] can produce. */
sealed class WorkflowError {
    /** The requested workflow does not exist or the caller does not own it. */
    object WorkflowNotFound : WorkflowError()
    /** The authenticated principal has no matching user record. */
    object UserNotFound : WorkflowError()
    /** The workflow is private and the caller is neither its owner nor an admin. */
    object AccessDenied : WorkflowError()
}

/** Task */

/** All typed failure cases that [org.workflow.service.TaskService] can produce. */
sealed class TaskError {
    /** The requested task does not exist. */
    object TaskNotFound : TaskError()
    /** The target workflow does not exist or is not accessible to the caller. */
    object WorkflowNotFound : TaskError()
    /** The authenticated principal has no matching user record. */
    object UserNotFound : TaskError()
    /** The task is already associated with the target workflow. */
    object AlreadyLinked : TaskError()
    /** The task has no association with the target workflow. */
    object NotLinked : TaskError()
    /** The task is private and the caller is neither its owner nor an admin. */
    object AccessDenied : TaskError()
}

/** User */

/** All typed failure cases that [org.workflow.service.UserService] can produce. */
sealed class UserError {
    /** The username is already registered. */
    object UsernameAlreadyTaken : UserError()

    /** The requested role does not exist in the database. */
    object RoleNotFound : UserError()

    /** No user with the given id exists. */
    object UserNotFound : UserError()
}

/** Schedule */

/** All typed failure cases that [org.workflow.service.ScheduleService] can produce. */
sealed class ScheduleError {
    object ScheduleNotFound : ScheduleError()
    object WorkflowNotFound : ScheduleError()
    object UserNotFound : ScheduleError()
    object InvalidCronExpression : ScheduleError()
}

/** Execution */

/** All typed failure cases that [org.workflow.service.ExecutionService] can produce. */
sealed class ExecutionError {
    object TaskNotFound : ExecutionError()
    object WorkflowNotFound : ExecutionError()
    object UserNotFound : ExecutionError()
}

