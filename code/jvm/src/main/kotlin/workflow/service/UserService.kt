package org.workflow.service

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.workflow.dto.RoleSummaryResponse
import org.workflow.dto.UserAdminResponse
import org.workflow.dto.UserCreateRequest
import org.workflow.dto.UserRoleUpdateRequest
import org.workflow.dto.UserResponse
import org.workflow.entity.User
import org.workflow.repository.RoleRepository
import org.workflow.repository.UserRepository
import org.workflow.service.utils.UserError
import org.workflow.utils.Either
import org.workflow.utils.failure
import org.workflow.utils.success

@Service
/** Handles user creation flow and role assignment. */
class UserService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder
) {

    @Transactional(readOnly = true)
    fun listUsers(): List<UserAdminResponse> =
        userRepository.findAllWithRoleAndPermissions().map { toAdminResponse(it) }

    @Transactional(readOnly = true)
    fun listRoles(): List<RoleSummaryResponse> =
        roleRepository.findAllWithPermissions().map { role ->
            RoleSummaryResponse(
                name = role.name,
                permissions = role.permissions.map { it.slug }.sorted()
            )
        }

    @Transactional
    fun updateUserRole(userId: java.util.UUID, request: UserRoleUpdateRequest): Either<UserError, UserAdminResponse> {
        val user = userRepository.findByIdWithRoleAndPermissions(userId)
            ?: return failure(UserError.UserNotFound)

        val role = roleRepository.findByNameWithPermissions(request.roleName.uppercase())
            ?: return failure(UserError.RoleNotFound)

        user.role = role
        val saved = userRepository.save(user)
        return success(toAdminResponse(saved))
    }

    @Transactional
    fun createUser(request: UserCreateRequest): Either<UserError, UserResponse> {
        if (userRepository.findByUsername(request.username) != null) {
            return failure(UserError.UsernameAlreadyTaken)
        }

        val resolvedRoleName = (request.roleName ?: "READER").uppercase()
        val role = roleRepository.findByName(resolvedRoleName)
            ?: return failure(UserError.RoleNotFound)

        val savedUser = userRepository.save(
            User(
                username = request.username,
                passwordValidation = passwordEncoder.encode(request.passwordPlain),
                role = role
            )
        )

        return success(UserResponse(savedUser.id, savedUser.username, role = savedUser.role.name))
    }

    private fun toAdminResponse(user: User): UserAdminResponse =
        UserAdminResponse(
            id = user.id,
            username = user.username,
            role = user.role.name,
            permissions = user.role.permissions.map { it.slug }.sorted()
        )
}
