package org.workflow.security

import jakarta.servlet.DispatcherType
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.client.RestTemplate
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.workflow.security.pipeline.RequestTokenProcessor

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
/**
 * Single authentication strategy: [CookieAuthenticationFilter] reads the `token` cookie on every
 * request, validates it against the database and populates the Spring Security [SecurityContextHolder]
 * so [@PreAuthorize] annotations work on all controllers.
 */
class SecurityConfig(
    @Lazy private val requestTokenProcessor: RequestTokenProcessor,
    @Lazy private val customUserDetailsService: CustomUserDetailsService,
    @Value("\${app.cors.allowed-origins:http://localhost:5173}") private val allowedOrigins: String
) : WebMvcConfigurer {

    /** Exposed as a bean so [org.workflow.service.AuthService] can inject it for BCrypt hashing. */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    /** Exposed as a bean so [org.workflow.service.ExecutionService] can be tested with a mock. */
    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()

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
            .addFilterBefore(cookieAuthenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests { auth ->
                // ASYNC and ERROR dispatches come from Tomcat's internal async/error handling
                // (e.g., SSE completion, timeout). No security context is available on those
                // threads, so permit them unconditionally — @PreAuthorize on each handler
                // already enforces fine-grained access on the original request.
                auth.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()

                auth.requestMatchers(
                    "/api/auth/**",
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

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}
