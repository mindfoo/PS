package org.workflow.service

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.workflow.dto.LoginRequest
import org.workflow.dto.ProfileResponse
import org.workflow.dto.RegisterRequest
import org.workflow.dto.TokenResponse
import org.workflow.entity.User
import org.workflow.entity.UserToken
import org.workflow.repository.RoleRepository
import org.workflow.repository.UserRepository
import org.workflow.repository.UserTokenRepository
import org.workflow.security.TokenUtils
import org.workflow.entity.enums.RoleType
import org.workflow.service.utils.AuthLoginError
import org.workflow.service.utils.AuthRegisterError
import org.workflow.utils.Either
import org.workflow.utils.failure
import org.workflow.utils.success
import java.time.LocalDateTime

/** Implements user registration, login (cookie token), logout and profile retrieval. */
@Service
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
    fun register(request: RegisterRequest): Either<AuthRegisterError, ProfileResponse> {
        if (!isSafePassword(request.password)) {
            return failure(AuthRegisterError.InsecurePassword)
        }

        if (userRepository.findByUsername(request.username) != null) {
            return failure(AuthRegisterError.UsernameAlreadyTaken)
        }

        val resolvedRoleName = (request.roleName ?: RoleType.READER.name).uppercase()
        val roleType = RoleType.fromString(resolvedRoleName)
            ?: return failure(AuthRegisterError.RoleNotFound)
        val role = roleRepository.findByName(roleType)
            ?: return failure(AuthRegisterError.RoleNotFound)

        val saved = userRepository.save(
            User(
                username = request.username,
                passwordValidation = passwordEncoder.encode(request.password),
                role = role
            )
        )
        return success(ProfileResponse(id = saved.id, username = saved.username, role = saved.role.name))
    }

    /**
     * Validates credentials directly with [PasswordEncoder.matches]
     */
    @Transactional
    fun login(request: LoginRequest): Either<AuthLoginError, TokenResponse> {
        if (request.username.isBlank() || request.password.isBlank()) {
            return failure(AuthLoginError.InvalidCredentials)
        }

        val user = userRepository.findByUsername(request.username)
            ?: return failure(AuthLoginError.InvalidCredentials)

        if (!passwordEncoder.matches(request.password, user.passwordValidation)) {
            return failure(AuthLoginError.InvalidCredentials)
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
     */
    @Transactional
    fun logout(rawToken: String) {
        userTokenRepository.deleteByTokenHash(TokenUtils.hashToken(rawToken))
    }

    @Transactional(readOnly = true)
    fun profile(username: String): Either<AuthLoginError, ProfileResponse> {
        val user = userRepository.findByUsername(username)
            ?: return failure(AuthLoginError.UserNotFound)

        return success(ProfileResponse(id = user.id, username = user.username, role = user.role.name))
    }

    /** Password must be at least 10 characters and contain uppercase, lowercase, and a symbol. */
    private fun isSafePassword(password: String): Boolean =
        password.length >= 10 &&
            password.any { it.isUpperCase() } &&
            password.any { it.isLowerCase() } &&
            password.any { !it.isLetterOrDigit() }
}
