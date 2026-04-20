package org.workflow.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.workflow.entity.enums.ActionType
import org.workflow.entity.enums.ResourceType
import java.util.UUID


@Entity
@Table(name = "permission")
/** Granular permission cataloguing which action is allowed on which resource. */
class Permission(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    val resource: ResourceType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    var action: ActionType
) : Timestamp() {

    /**
     * Human-readable slug derived from resource and action (e.g. `workflow:read`).
     * Not persisted — computed at runtime. Used by [org.workflow.security.CustomUserDetailsService].
     */
    @get:Transient
    @Suppress("unused")
    val slug: String
        get() = "${resource.name.lowercase()}:${action.name.lowercase()}"
}
