package org.workflow.repository

import org.workflow.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

/** Data access operations for users. */
@Repository
interface UserRepository : JpaRepository<User, UUID> {

    fun findByUsername(username: String): User?

    /**
     * Loads user → role → permissions in a single SQL JOIN FETCH.
     */
    @Query("""
        SELECT u FROM User u
        JOIN FETCH u.role r
        LEFT JOIN FETCH r.permissions
        WHERE u.username = :username
    """)
    fun findByUsernameWithPermissions(@Param("username") username: String): User?

    @Query("""
        SELECT DISTINCT u FROM User u
        JOIN FETCH u.role r
        LEFT JOIN FETCH r.permissions
        ORDER BY u.username ASC
    """)
    fun findAllWithRoleAndPermissions(): List<User>

    @Query("""
        SELECT DISTINCT u FROM User u
        JOIN FETCH u.role r
        LEFT JOIN FETCH r.permissions
        WHERE u.id = :id
    """)
    fun findByIdWithRoleAndPermissions(@Param("id") id: UUID): User?
}