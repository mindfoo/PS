package org.workflow.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() } // Disabled for basic API development; enable later for production
            .authorizeHttpRequests { auth ->
                // Anyone can register
                auth.requestMatchers("/api/users/register").permitAll()

                // Only Admins and Writers can access workflow creation
                auth.requestMatchers("/api/workflows/**").hasAnyRole("ADMIN", "WRITE")

                // Readers can only access logs
                auth.requestMatchers("/api/logs/**").hasAnyRole("ADMIN", "WRITE", "READER", "DEV")

                auth.anyRequest().authenticated()
            }

        return http.build()
    }
}