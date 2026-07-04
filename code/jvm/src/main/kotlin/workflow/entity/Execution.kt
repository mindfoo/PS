package org.workflow.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "executions")
/** Persisted execution record for workflow or task runs. */
class Execution(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false, length = 64)
    var triggeredType: String, // MANUAL | CRON

    @Column(nullable = false, length = 64)
    var type: String, // TASK, WORKFLOW

    @Column(nullable = false, length = 64)
    var status: String, // PENDING, RUNNING, SUCCESS, ERROR

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var output: Map<String, Any>? = null,

    @Column(nullable = false)
    var startedAt: LocalDateTime = LocalDateTime.now(),

    var finishedAt: LocalDateTime? = null,

    @Column(nullable = false)
    var retryCount: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by", nullable = false)
    var triggeredBy: User,

    /** Set on task-level execution records; null on top-level workflow executions. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    var task: Task? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id")
    var workflow: Workflow? = null,

    /** ID of the parent workflow execution (populated on child task-level records). */
    @Column(name = "parent_execution_id")
    var parentExecutionId: UUID? = null,

    /** Snapshot of the task name at execution time (useful after task renames/deletes). */
    @Column(name = "task_name", length = 128)
    var taskName: String? = null,

    /** Signals that a cancel was requested while the execution was PENDING or RUNNING. */
    @Column(nullable = false)
    var cancelRequested: Boolean = false
) : Timestamp()