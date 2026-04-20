package org.workflow.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(name = "tasks")
/** Task entity belonging to a workflow with JSONB execution config. */
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    var workflow_id: Workflow
) : Timestamp()