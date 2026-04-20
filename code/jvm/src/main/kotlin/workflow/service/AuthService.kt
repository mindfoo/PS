package org.workflow.service

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.workflow.dto.LoginRequest
import org.workflow.dto.MeResponse
import org.workflow.dto.RegisterRequest
import org.workflow.dto.TokenResponse
import org.workflow.entity.User
import org.workflow.entity.UserToken
import org.workflow.repository.RoleRepository
import org.workflow.repository.UserRepository
import org.workflow.repository.UserTokenRepository
import org.workflow.security.TokenUtils
import org.workflow.service.utils.AuthError
import org.workflow.utils.Either
import org.workflow.utils.failure
import org.workflow.utils.success
import java.time.LocalDateTime

@Service
/** Implements user registration, login (cookie token), logout and profile retrieval. */
class AuthService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val userTokenRepository: UserTokenRepository,
    private val passwordEncoder: PasswordEncoder
) {

    companion object {
        /** Token lifetime — matches the cookie maxAge set by the controller. */
        const val TOKEN_TTL_HOURS = 24L
    }

    @Transactional
    fun register(request: RegisterRequest): Either<AuthError, MeResponse> {
        if (userRepository.findByUsername(request.username) != null) {
            return failure(AuthError.UsernameAlreadyTaken)
        }

        val resolvedRoleName = (request.roleName ?: "READER").uppercase()
        val role = roleRepository.findByName(resolvedRoleName)
            ?: return failure(AuthError.RoleNotFound)

        val saved = userRepository.save(
            User(
                username = request.username,
                passwordValidation = passwordEncoder.encode(request.password),
                role = role
            )
        )
        return success(MeResponse(id = saved.id, username = saved.username, role = saved.role.name))
    }

    /**
     * Validates credentials directly with [PasswordEncoder.matches] — no Spring Security
     * [AuthenticationManager] involved — then generates an opaque random token, stores its
     * SHA-256 hash in [UserToken] and returns the raw value for the cookie.
     *
     */
    @Transactional
    fun login(request: LoginRequest): Either<AuthError, TokenResponse> {
        if (request.username.isBlank() || request.password.isBlank()) {
            return failure(AuthError.InvalidCredentials)
        }

        val user = userRepository.findByUsername(request.username)
            ?: return failure(AuthError.InvalidCredentials)

        if (!passwordEncoder.matches(request.password, user.passwordValidation)) {
            return failure(AuthError.InvalidCredentials)
        }

        val rawToken = TokenUtils.generateToken()
        userTokenRepository.save(
            UserToken(
                tokenHash = TokenUtils.hashToken(rawToken),
                user = user,
                expiresAt = LocalDateTime.now().plusHours(TOKEN_TTL_HOURS)
            )
        )

        return success(TokenResponse(accessToken = rawToken))
    }

    /**
     * Revokes the token by deleting its hash from the database.
     * The controller clears the cookie client-side after calling this.
     */
    @Transactional
    fun logout(rawToken: String) {
        userTokenRepository.deleteByTokenHash(TokenUtils.hashToken(rawToken))
    }

    fun me(username: String): Either<AuthError, MeResponse> {
        val user = userRepository.findByUsername(username)
            ?: return failure(AuthError.UserNotFound)

        return success(MeResponse(id = user.id, username = user.username, role = user.role.name))
    }
}
