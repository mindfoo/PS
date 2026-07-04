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
 * Intercepts every HTTP request and authenticates it from the "token" cookie.
 *
 * This filter runs once per request (OncePerRequestFilter) and does the following:
 *
 *   1. Reads the raw token value from the HttpOnly cookie named "token".
 *   2. Delegates to RequestTokenProcessor, which hashes the value and looks it up in the DB.
 *      If the hash exists and the token has not expired, the processor returns the User.
 *   3. Loads the full UserDetails (with role authorities like "workflow:read") from the DB.
 *   4. Wraps everything in a UsernamePasswordAuthenticationToken and places it in the
 *      SecurityContextHolder — the global holder Spring Security checks on every thread.
 *   5. Calls filterChain.doFilter() to pass the request to the next filter / controller.
 *
 * If no cookie is present, or the token is invalid/expired, the SecurityContextHolder is left
 * empty. Spring Security will then block the request with 401 if the endpoint requires auth.
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
        /* Step 1 — read the raw token from the cookie jar. */
        val tokenValue = request.cookies?.find { it.name == COOKIE_NAME }?.value

        /* Step 2 — only attempt authentication if a token is present AND the request
           is not already authenticated (avoids redundant DB lookups on forwarded requests). */
        if (!tokenValue.isNullOrBlank() && SecurityContextHolder.getContext().authentication == null) {

            /* Step 3 — validate token hash against DB and get the User back. */
            val user = requestTokenProcessor.processAuthorizationCookieValue(tokenValue)

            if (user != null) {
                /* Step 4 — load the full UserDetails including granted authorities. */
                val userDetails = customUserDetailsService.loadUserByUsername(user.username)

                /* Step 5 — build the authentication object and store it in the SecurityContext.
                   null password is intentional — the token IS the credential at this point. */
                val authToken = UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.authorities
                )
                authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authToken
            }
        }

        /* Always continue the filter chain — Spring Security decides downstream whether
           to block the request based on what is (or is not) in the SecurityContextHolder. */
        filterChain.doFilter(request, response)
    }

    companion object {
        const val COOKIE_NAME = "token"
        const val USERNAME_COOKIE_NAME = "username"
    }
}

