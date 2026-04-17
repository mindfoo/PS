package org.workflow.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "workflows")
/** Workflow aggregate root owned by a user and composed of tasks. */
class Workflow(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false, length = 64)
    var name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var created_by: User
) : Timestamp()