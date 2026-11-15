package com.apkrepo.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

/**
 * Security setup for the MVP: a single administrator account whose
 * credentials come from application configuration / environment variables.
 *
 * - Public catalog, repository info, health, Swagger and OpenAPI endpoints
 *   are readable anonymously.
 * - Everything under the /api/v1/admin path prefix requires the ADMIN role.
 * - The API is stateless and uses HTTP Basic authentication, which works
 *   directly from Swagger UI and scripts.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(private val properties: ApkRepoProperties) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun userDetailsService(passwordEncoder: PasswordEncoder): UserDetailsService {
        val admin = User.withUsername(properties.admin.username)
            .password(passwordEncoder.encode(properties.admin.password))
            .roles("ADMIN")
            .build()
        return InMemoryUserDetailsManager(admin)
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/api/v1/apps/**",
                        "/api/v1/repository",
                        "/api/v1/releases/**",
                        "/api/v1/icons/**",
                        "/fdroid/**",
                        "/actuator/health",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/error",
                    ).permitAll()
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                    .anyRequest().permitAll()
            }
            .httpBasic { }
        return http.build()
    }
}
