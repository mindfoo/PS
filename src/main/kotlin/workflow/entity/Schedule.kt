package org.workflow.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "schedules")
/** Cron schedule configuration linked to a workflow execution plan. */
class Schedule(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    var workflow: Workflow,

    @Column(nullable = false, length = 128)
    var cronExpression: String,

    @Column(nullable = false, length = 64)
    var timezone: String = "UTC",

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(nullable = false)
    var nextRunAt: LocalDateTime,

    var lastRunAt: LocalDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    var createdBy: User
) : Timestamp()

