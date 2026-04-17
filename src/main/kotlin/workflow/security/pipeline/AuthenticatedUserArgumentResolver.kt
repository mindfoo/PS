package org.workflow.security.pipeline

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * Resolves [AuthenticatedUser] controller parameters from the request attribute
 * set by [AuthenticationInterceptor].
 */
@Component
class AuthenticatedUserArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == AuthenticatedUser::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): Any? {
        val request = webRequest.getNativeRequest(HttpServletRequest::class.java)
            ?: throw IllegalStateException("No HttpServletRequest available")
        return getUserFrom(request) ?: throw IllegalStateException("AuthenticatedUser not set — interceptor skipped")
    }

    companion object {
        private const val KEY = "AuthenticatedUser"

        fun addUserTo(user: AuthenticatedUser, request: HttpServletRequest) {
            request.setAttribute(KEY, user)
        }

        fun getUserFrom(request: HttpServletRequest): AuthenticatedUser? =
            request.getAttribute(KEY) as? AuthenticatedUser
    }
}

