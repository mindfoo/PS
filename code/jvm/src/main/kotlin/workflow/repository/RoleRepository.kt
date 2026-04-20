package org.workflow.repository


import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.workflow.entity.Roles
import java.util.UUID

@Repository
/** Data access operations for RBAC roles. */
interface RoleRepository : JpaRepository<Roles, UUID> {
    fun findByName(name: String): Roles?
}