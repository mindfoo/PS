package org.workflow.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "alerts")
/** Alert generated from execution events. */
class Alert(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    var execution: Execution,

    @Column(nullable = false, length = 64)
    var type: String,

    @Column(nullable = false, length = 64)
    var event: String
) : Timestamp()