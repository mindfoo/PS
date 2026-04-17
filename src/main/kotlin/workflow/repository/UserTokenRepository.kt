package org.workflow.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.workflow.entity.UserToken
import java.util.UUID

@Repository
/** Data access operations for user authentication tokens. */
interface UserTokenRepository : JpaRepository<UserToken, UUID> {

    /**
     * Loads the token together with user → role → permissions in one query to avoid
     * LazyInitializationException when the security filter accesses authorities.
     */
    @Query("""
        SELECT t FROM UserToken t
        JOIN FETCH t.user u
        JOIN FETCH u.role r
        LEFT JOIN FETCH r.permissions
        WHERE t.tokenHash = :hash
    """)
    fun findByTokenHashWithUser(@Param("hash") hash: String): UserToken?

    fun deleteByTokenHash(tokenHash: String)

    @Modifying
    @Query("DELETE FROM UserToken t WHERE t.user.id = :userId")
    fun deleteAllByUserId(@Param("userId") userId: UUID)
}

