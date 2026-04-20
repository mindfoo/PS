package org.workflow.security.pipeline

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Reads the [COOKIE_NAME] cookie from each request and enforces authentication
 * on handler methods that declare an [AuthenticatedUser] parameter.
 */
@Component
class AuthenticationInterceptor(
    private val requestTokenProcessor: RequestTokenProcessor
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        if (handler is HandlerMethod &&
            handler.methodParameters.any { it.parameterType == AuthenticatedUser::class.java }
        ) {
            val tokenValue = request.cookies?.find { it.name == COOKIE_NAME }?.value
            val user = requestTokenProcessor.processAuthorizationCookieValue(tokenValue)

            return if (user == null) {
                response.status = 401
                response.addHeader("WWW-Authenticate", COOKIE_NAME)
                false
            } else {
                AuthenticatedUserArgumentResolver.addUserTo(user, request)
                true
            }
        }

        return true
    }

    companion object {
        const val COOKIE_NAME = "token"
        const val USERNAME_COOKIE_NAME = "username"
    }
}

