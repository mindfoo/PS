package org.workflow.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.workflow.entity.Permission
import java.util.UUID

/** Data access operations for the permission catalogue. */
@Repository
interface PermissionRepository : JpaRepository<Permission, UUID>


