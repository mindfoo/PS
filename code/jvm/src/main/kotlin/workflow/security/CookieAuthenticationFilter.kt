package org.workflow.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter
import org.workflow.security.pipeline.AuthenticationInterceptor
import org.workflow.security.pipeline.RequestTokenProcessor

/**
 * Reads the [AuthenticationInterceptor.COOKIE_NAME] cookie on every request and, when a valid
 * token is found, populates the Spring Security [SecurityContextHolder] so that
 * method-level [@PreAuthorize] annotations continue to work.
 *
 * This complements [AuthenticationInterceptor]: the interceptor covers controllers
 * that inject [org.workflow.security.pipeline.AuthenticatedUser] directly,
 * while this filter covers the RBAC guard on existing endpoints.
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
        val tokenValue = request.cookies?.find { it.name == AuthenticationInterceptor.COOKIE_NAME }?.value

        if (!tokenValue.isNullOrBlank() && SecurityContextHolder.getContext().authentication == null) {
            val authenticatedUser = requestTokenProcessor.processAuthorizationCookieValue(tokenValue)
            if (authenticatedUser != null) {
                val userDetails = customUserDetailsService.loadUserByUsername(authenticatedUser.user.username)
                val authToken = UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.authorities
                )
                authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authToken
            }
        }

        filterChain.doFilter(request, response)
    }
}

