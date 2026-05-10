package org.workflow.repository


import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.workflow.entity.Roles
import java.util.UUID

@Repository
/** Data access operations for RBAC roles. */
interface RoleRepository : JpaRepository<Roles, UUID> {
    fun findByName(name: String): Roles?

    @Query("select distinct r from Roles r left join fetch r.permissions order by r.name asc")
    fun findAllWithPermissions(): List<Roles>

    @Query("select distinct r from Roles r left join fetch r.permissions where r.name = :name")
    fun findByNameWithPermissions(@Param("name") name: String): Roles?
}