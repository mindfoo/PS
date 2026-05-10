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

    /** Retained for backward compatibility; new tasks may have null here. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = true)
    var workflow_id: Workflow? = null,

    /** User who created this task — used for ownership checks when workflow_id is null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = true)
    var createdBy: User? = null
) : Timestamp()
