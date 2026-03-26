package org.workflow.service

import org.workflow.dto.UserCreateRequest
import org.workflow.dto.UserResponse
import org.workflow.entity.User
import org.workflow.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.workflow.repository.RoleRepository

@Service
class UserService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder
) {

    fun createUser(request: UserCreateRequest): UserResponse {
        if (userRepository.findByUsername(request.username) != null) {
            throw IllegalArgumentException("Username is already taken!")
        }

        val assignedRole = roleRepository.findByName(request.roleName)
            ?: throw IllegalArgumentException("Role '${request.roleName}' does not exist in the database!")

        val newUser = User(
            username = request.username,
            passwordHash = passwordEncoder.encode(request.passwordPlain),
            roles = mutableSetOf(assignedRole)
        )

        val savedUser = userRepository.save(newUser)

        return UserResponse(savedUser.id, savedUser.username, roles = savedUser.roles.map { it.name })
    }
}
