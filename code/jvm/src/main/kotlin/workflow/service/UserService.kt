package org.workflow.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.workflow.dto.RoleSummaryResponse
import org.workflow.dto.UserAdminResponse
import org.workflow.dto.UserRoleUpdateRequest
import org.workflow.entity.User
import org.workflow.repository.RoleRepository
import org.workflow.repository.UserRepository
import org.workflow.entity.enums.RoleType
import org.workflow.service.utils.UserError
import org.workflow.utils.Either
import org.workflow.utils.failure
import org.workflow.utils.success

/** Handles user creation flow and role assignment. */
@Service
class UserService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository
) {

    @Transactional(readOnly = true)
    fun listUsers(): List<UserAdminResponse> =
        userRepository.findAllWithRoleAndPermissions().map { it.toAdminResponse() }

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

        val roleType = RoleType.fromString(request.roleName)
            ?: return failure(UserError.RoleNotFound)
        val role = roleRepository.findByNameWithPermissions(roleType)
            ?: return failure(UserError.RoleNotFound)

        user.role = role
        val saved = userRepository.save(user)
        return success(saved.toAdminResponse())
    }

    private fun User.toAdminResponse(): UserAdminResponse =
        UserAdminResponse(
            id = id,
            username = username,
            role = role.name,
            permissions = role.permissions.map { it.slug }.sorted()
        )
}
