package org.workflow.entity

/** Valid values for [Execution.status]. */
object ExecutionStatus {
    const val PENDING  = "PENDING"
    const val RUNNING  = "RUNNING"
    const val SUCCESS  = "SUCCESS"
    const val ERROR    = "ERROR"
    const val CANCELED = "CANCELED"
}

/** Valid values for [Execution.triggeredType]. */
object ExecutionTriggerType {
    const val MANUAL = "MANUAL"
    const val CRON   = "CRON"
}

/** Valid values for [Execution.type]. */
object ExecutionType {
    const val TASK     = "TASK"
    const val WORKFLOW = "WORKFLOW"
}

object TaskType {
    const val SCRIPT = "SCRIPT"
    const val HTTP   = "HTTP"
}
