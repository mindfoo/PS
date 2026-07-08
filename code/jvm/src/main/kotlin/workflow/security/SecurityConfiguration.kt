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
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.workflow.security.pipeline.RequestTokenProcessor

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfiguration(
    @Lazy private val requestTokenProcessor: RequestTokenProcessor,
    @Lazy private val customUserDetailsService: CustomUserDetailsService,
    @Value("\${app.cors.allowed-origins:http://localhost:5173}") private val allowedOrigins: String
) : WebMvcConfigurer {

    private val allowedOriginsList: Array<String> = allowedOrigins.split(",").map { it.trim() }.toTypedArray()

    /** BCrypt password encoder — injected into AuthService for hashing and verification. */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun cookieAuthenticationFilter(): CookieAuthenticationFilter =
        CookieAuthenticationFilter(requestTokenProcessor, customUserDetailsService)

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            /* No server-side session — each request is authenticated from its cookie alone. */
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            /* Disable Spring's built-in form login and HTTP Basic — we handle auth ourselves. */
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .csrf { it.disable() }
            /* Run our cookie filter before Spring's default username/password filter. */
            .addFilterBefore(cookieAuthenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests { auth ->
                /* ASYNC and ERROR dispatches are internal Tomcat callbacks (e.g., SSE completion).
                   They run on a different thread where the security context is not available,
                   so we permit them here — the original HTTP request was already authenticated. */
                auth.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()

                /* Public endpoints — no cookie required. */
                auth.requestMatchers(
                    "/api/auth/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()

                /* All other API endpoints require a valid cookie.
                   Fine-grained authority checks (e.g. workflow:read) are on each controller method
                   via @PreAuthorize — keeping the rules close to the code that uses them. */
                auth.anyRequest().authenticated()
            }

        return http.build()
    }

    /** Allow the frontend Vite dev server (or the configured origin) to call the API. */
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins(*allowedOriginsList)
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}
