package org.workflow.repository

import org.workflow.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
/** Data access operations for users. */
interface UserRepository : JpaRepository<User, UUID> {

    /** Basic lookup — no eager loading of permissions. */
    fun findByUsername(username: String): User?

    /**
     * Loads user → role → permissions in a single SQL JOIN FETCH.
     * Must be used by [org.workflow.security.CustomUserDetailsService] to avoid
     * LazyInitializationException when the Hibernate session is closed after the call.
     */
    @Query("""
        SELECT u FROM User u
        JOIN FETCH u.role r
        LEFT JOIN FETCH r.permissions
        WHERE u.username = :username
    """)
    fun findByUsernameWithPermissions(@Param("username") username: String): User?
}