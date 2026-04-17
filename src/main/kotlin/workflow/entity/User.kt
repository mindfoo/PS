package org.workflow.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "users")
/** User account entity used for authentication and ownership checks. */
class User(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(unique = true, nullable = false, length = 64)
    var username: String,

    @Column(nullable = false, length = 256)
    var passwordValidation: String,

    @Column(length = 64)
    var displayName: String? = null,

    // Relação 1:N com as roles
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    var role: Roles
) : Timestamp()