package org.workflow.security.pipeline

import org.workflow.entity.User

/**
 * Wraps an authenticated user together with the raw token value that identified them.
 */
class AuthenticatedUser(
    val user: User,
    val token: String
)

