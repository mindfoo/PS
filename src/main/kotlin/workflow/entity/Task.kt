package org.workflow.entity

import jakarta.persistence.*

@Entity
@Table(name = "tasks")
class Task(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var name: String,

    @Column(columnDefinition = "TEXT", nullable = false)
    var scriptContent: String,

    @Column(nullable = false)
    var maxRetries: Int = 3,

    // Each task belongs to a workflow
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    val workflow: Workflow
)