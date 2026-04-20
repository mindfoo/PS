package org.workflow.security

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.workflow.repository.UserRepository

@Service
/** Loads application users and their full permission set for Spring Security authentication. */
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    /**
     * @Transactional(readOnly = true) keeps the Hibernate session open during the entire method,
     * so that the LAZY permissions collection on role can be accessed safely.
     * The JOIN FETCH query also pre-loads everything in a single SQL call.
     */
    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByUsernameWithPermissions(username)
            ?: throw UsernameNotFoundException("User '$username' not found")

        val role = user.role
        val roleName = role.name.uppercase()

        // Coarse-grained authority: ROLE_ADMIN, ROLE_WRITER, ROLE_READER, ROLE_DEV
        val roleAuthority = SimpleGrantedAuthority("ROLE_$roleName")

        // Fine-grained authorities from the permission catalogue: workflow:read, task:write, etc.
        val permissionAuthorities = role.permissions.map { SimpleGrantedAuthority(it.slug) }

        return User.builder()
            .username(user.username)
            .password(user.passwordValidation)
            .authorities(listOf(roleAuthority) + permissionAuthorities)
            .build()
    }
}
