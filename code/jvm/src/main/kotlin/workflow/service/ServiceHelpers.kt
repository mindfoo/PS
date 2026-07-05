package org.workflow.service

import org.springframework.stereotype.Component
import org.workflow.entity.User
import org.workflow.entity.enums.RoleType
import org.workflow.repository.UserRepository
import java.util.UUID

/** Shared helper methods used across multiple service classes. */
@Component
class ServiceHelpers(private val userRepository: UserRepository) {

    /** Returns the user with the given username, or null if not found. */
    fun findUser(username: String): User? =
        userRepository.findByUsername(username)

    /** Returns true if the user has the ADMIN role. */
    fun isAdmin(user: User): Boolean =
        user.role.name == RoleType.ADMIN

}

/** Admins can look up any resource by id; other users are restricted to resources they own. */
fun <T> findOwned(isAdmin: Boolean, userId: UUID?, byId: () -> T?, byOwner: (UUID) -> T?): T? {
    if (userId == null) return null
    return if (isAdmin) byId() else byOwner(userId)
}
