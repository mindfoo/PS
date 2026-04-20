package org.workflow.security.pipeline

import org.springframework.stereotype.Component
import org.workflow.repository.UserTokenRepository
import org.workflow.security.TokenUtils
import java.time.LocalDateTime

/**
 * Validates the raw token value read from the cookie against the hashed entry in the database.
 */
@Component
class RequestTokenProcessor(
    private val userTokenRepository: UserTokenRepository
) {
    fun processAuthorizationCookieValue(tokenValue: String?): AuthenticatedUser? {
        if (tokenValue.isNullOrBlank()) return null

        val hash = TokenUtils.hashToken(tokenValue)
        val userToken = userTokenRepository.findByTokenHashWithUser(hash) ?: return null

        if (userToken.expiresAt.isBefore(LocalDateTime.now())) return null

        return AuthenticatedUser(user = userToken.user, token = tokenValue)
    }
}

