package org.workflow.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.workflow.security.pipeline.AuthenticatedUserArgumentResolver
import org.workflow.security.pipeline.AuthenticationInterceptor
import org.workflow.security.pipeline.RequestTokenProcessor

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
/**
 *
 * - Password is validated
 *   directly in [org.workflow.service.AuthService] with [PasswordEncoder.matches].
 * - Authentication is enforced by [CookieAuthenticationFilter] (populates Spring Security
 *   context so [@PreAuthorize] works) and [AuthenticationInterceptor] (injects
 *   [org.workflow.security.pipeline.AuthenticatedUser] into controller parameters).
 */
class SecurityConfig(
    private val requestTokenProcessor: RequestTokenProcessor,
    private val customUserDetailsService: CustomUserDetailsService,
    private val authenticationInterceptor: AuthenticationInterceptor,
    private val authenticatedUserArgumentResolver: AuthenticatedUserArgumentResolver
) : WebMvcConfigurer {

    /** Exposed as a bean so [org.workflow.service.AuthService] can inject it for BCrypt hashing. */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun cookieAuthenticationFilter(): CookieAuthenticationFilter =
        CookieAuthenticationFilter(requestTokenProcessor, customUserDetailsService)

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            // Cookie filter runs before the standard username/password filter.
            // It reads the 'token' cookie, validates against the DB and populates
            // the SecurityContext so @PreAuthorize annotations keep working.
            .addFilterBefore(cookieAuthenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(
                    "/api/auth/**",
                    "/api/users/register",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()

                auth.requestMatchers("/api/workflows/**").hasAnyRole("ADMIN", "WRITER", "READER", "DEV")
                auth.requestMatchers("/api/tasks/**").hasAnyRole("ADMIN", "WRITER", "READER", "DEV")
                auth.requestMatchers("/api/schedules/**").hasAnyRole("ADMIN", "WRITER", "READER")
                auth.requestMatchers("/api/logs/**").hasAnyRole("ADMIN", "WRITER", "READER", "DEV")

                auth.anyRequest().authenticated()
            }

        return http.build()
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authenticationInterceptor)
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(authenticatedUserArgumentResolver)
    }
}
