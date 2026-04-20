package org.workflow.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Utilities for opaque token generation and hashing.
 *
 * The raw token is a cryptographically random Base64Url string (256 bits).
 * Only its SHA-256 hash is persisted in the database — the raw value lives exclusively in the cookie.
 */
object TokenUtils {

    private const val TOKEN_SIZE_BYTES = 32 // 256 bits

    /** Generate a new cryptographically random token value to be placed in the cookie. */
    fun generateToken(): String =
        ByteArray(TOKEN_SIZE_BYTES)
            .also { SecureRandom.getInstanceStrong().nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    /** Produce the SHA-256 hash of [token] for safe storage in the database. */
    fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(digest.digest(token.toByteArray(Charsets.UTF_8)))
    }
}

