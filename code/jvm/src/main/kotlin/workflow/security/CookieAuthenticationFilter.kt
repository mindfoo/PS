package org.workflow.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter
import org.workflow.security.pipeline.RequestTokenProcessor

/**
 * Single authentication strategy: reads the [COOKIE_NAME] cookie on every request and, when a
 * valid token is found, populates the Spring Security [SecurityContextHolder] so that
 * method-level [@PreAuthorize] annotations work on all controllers.
 */
class CookieAuthenticationFilter(
    private val requestTokenProcessor: RequestTokenProcessor,
    private val customUserDetailsService: CustomUserDetailsService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val tokenValue = request.cookies?.find { it.name == COOKIE_NAME }?.value

        if (!tokenValue.isNullOrBlank() && SecurityContextHolder.getContext().authentication == null) {
            val user = requestTokenProcessor.processAuthorizationCookieValue(tokenValue)
            if (user != null) {
                val userDetails = customUserDetailsService.loadUserByUsername(user.username)
                val authToken = UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.authorities
                )
                authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authToken
            }
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        const val COOKIE_NAME = "token"
        const val USERNAME_COOKIE_NAME = "username"
    }
}

