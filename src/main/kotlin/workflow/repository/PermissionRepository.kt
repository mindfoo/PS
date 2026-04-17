package org.workflow.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.workflow.entity.Permission
import org.workflow.entity.enums.ActionType
import org.workflow.entity.enums.ResourceType
import java.util.UUID

@Repository
/** Data access operations for the permission catalogue. */
interface PermissionRepository : JpaRepository<Permission, UUID> {
    @Suppress("unused")
    fun findByResourceAndAction(resource: ResourceType, action: ActionType): Permission?
}


