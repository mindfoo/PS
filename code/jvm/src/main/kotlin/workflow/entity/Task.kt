package org.workflow.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(name = "tasks")
/** Standalone task entity. Linked to workflows via WorkflowTaskOrder (many-to-many). */
class Task(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false, length = 64)
    var name: String,

    @Column(nullable = false, length = 64)
    var type: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var config: Map<String, Any> = mutableMapOf(),

    /** Direct workflow scope — set when a task is created inside a workflow context; null for standalone tasks. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = true)
    var workflow: Workflow? = null,

    /** User who created this task — used for ownership checks when workflow is null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = true)
    var createdBy: User? = null,

    /** When true, only the creator and admins can view this task. */
    @Column(nullable = false)
    var isPrivate: Boolean = false
) : Timestamp()
