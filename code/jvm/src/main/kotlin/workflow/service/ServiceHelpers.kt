package org.workflow.service

import org.springframework.stereotype.Component
import org.workflow.entity.User
import org.workflow.repository.UserRepository

/** Shared helper methods used across multiple service classes. */
@Component
class ServiceHelpers(private val userRepository: UserRepository) {

    /** Returns the user with the given username, or null if not found. */
    fun findUser(username: String): User? =
        userRepository.findByUsername(username)

    /** Returns the user with the given username, throwing if not found. */
    fun requireUser(username: String): User =
        userRepository.findByUsername(username)
            ?: throw NoSuchElementException("User '$username' not found")

    /** Returns true if the user has the ADMIN role. */
    fun isAdmin(user: User): Boolean =
        user.role.name.equals("ADMIN", ignoreCase = true)
}
