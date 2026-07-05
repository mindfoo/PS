package org.workflow.entity

import jakarta.persistence.*
import org.workflow.entity.enums.RoleType
import java.util.UUID

@Entity
@Table(name = "roles")
/** RBAC role entity assigned to users: WRITER, READER, ADMIN */
class Roles(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(unique = true, nullable = false, length = 64)
    var name: RoleType,

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "role_permission",
        joinColumns = [JoinColumn(name = "role_id")],
        inverseJoinColumns = [JoinColumn(name = "permission_id")]
    )
    val permissions: MutableSet<Permission> = mutableSetOf()

): Timestamp()