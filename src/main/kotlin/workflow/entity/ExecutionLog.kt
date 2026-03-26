package org.workflow.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "execution_logs")
class ExecutionLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    val task: Task,

    @Column(nullable = false)
    var status: String, // "PENDING", "RUNNING", "SUCCESS", "FAILED"

    var startTime: LocalDateTime? = null,
    var endTime: LocalDateTime? = null,
    var durationMs: Long? = null,

    @Column(columnDefinition = "TEXT")
    var resultOutput: String? = null,

    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null
)