package org.workflow.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "roles")
/** RBAC role entity assigned to users: WRITER, READER, ADMIN */
class Roles(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(unique = true, nullable = false, length = 64)
    var name: String,

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "role_permission",
        joinColumns = [JoinColumn(name = "role_id")],
        inverseJoinColumns = [JoinColumn(name = "permission_id")]
    )
    @Suppress("unused") // accessed at runtime by CustomUserDetailsService via role.permissions
    val permissions: MutableSet<Permission> = mutableSetOf()

): Timestamp()