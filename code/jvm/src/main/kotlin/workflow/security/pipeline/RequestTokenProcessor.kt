package org.workflow.security.pipeline

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.workflow.entity.User
import org.workflow.repository.UserTokenRepository
import org.workflow.security.TokenUtils
import java.time.LocalDateTime

/**
 * Validates the raw token value read from the cookie against the hashed entry in the database.
 * Returns the authenticated [User] or null if the token is absent, unknown, or expired.
 */
@Component
class RequestTokenProcessor(
    private val userTokenRepository: UserTokenRepository
) {
    @Transactional
    fun processAuthorizationCookieValue(tokenValue: String?): User? {
        if (tokenValue.isNullOrBlank()) return null

        val hash = TokenUtils.hashToken(tokenValue)
        val userToken = userTokenRepository.findByTokenHashWithUser(hash) ?: return null

        if (userToken.expiresAt.isBefore(LocalDateTime.now())) return null

        userToken.lastUsedAt = LocalDateTime.now()
        userTokenRepository.save(userToken)

        return userToken.user
    }
}

