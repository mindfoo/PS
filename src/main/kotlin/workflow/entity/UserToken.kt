package org.workflow.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "user_tokens")
/** Persisted authentication token linked to a user account. Raw token is never stored — only its SHA-256 hash. */
class UserToken(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    /** SHA-256 hash of the raw token value sent to the client via cookie. */
    @Column(nullable = false, unique = true, length = 256)
    var tokenHash: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var lastUsedAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var expiresAt: LocalDateTime
)

