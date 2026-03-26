package org.workflow.repository


import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.workflow.entity.Role

@Repository
interface RoleRepository : JpaRepository<Role, Long> {
    fun findByName(name: String): Role?
}