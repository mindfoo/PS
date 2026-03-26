package org.workflow.entity

import jakarta.persistence.*

// Bucket of permissions
@Entity
@Table(name = "roles")
class Role(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(unique = true, nullable = false)
    var name: String, // e.g., "ROLE_ADMIN", "ROLE_WRITER"

    // A role can have many perissions, and a permission can belong to many roles
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions", // Creates a linking table in Postgres
        joinColumns = [JoinColumn(name = "role_id")],
        inverseJoinColumns = [JoinColumn(name = "permission_id")]
    )
    var permissions: MutableSet<Permission> = mutableSetOf()
)