package org.workflow.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "workflow_tasks_order")
/** Execution ordering and dependency metadata for tasks within a workflow. */
class WorkflowTaskOrder(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    var workflow: Workflow,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    var task: Task,

    // Nullable porque a primeira tarefa não tem dependência!
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depends_on_task_id")
    var dependsOnTask: Task? = null,

    @Column(name = "task_order", nullable = false)
    var taskOrder: Int,

    @Column(nullable = false)
    var retryPolicy: Int = 0
) : Timestamp()