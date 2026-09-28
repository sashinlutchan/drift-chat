package com.drift.user.common

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler

/**
 * Shared security setup repeated in every service (spec sections 19 & 21):
 *  - JWT authentication from the `drift_session` cookie (no `Authorization` header)
 *  - CSRF double-submit via `CookieCsrfTokenRepository` (XSRF-TOKEN cookie readable by
 *    JS, echoed by Axios in the X-XSRF-TOKEN header)
 *  - stateless sessions (the JWT is the session)
 */
@Configuration
@EnableWebSecurity
class SecurityBeans(
    @Value("\${SESSION_COOKIE_NAME:drift_session}") private val cookieName: String,
) {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        cookieBearerTokenResolver: CookieBearerTokenResolver,
    ): SecurityFilterChain {
        val csrfHandler = CsrfTokenRequestAttributeHandler()
        http
            .csrf { csrf ->
                csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrfHandler)
            }.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints (spec section 14): sign-up, username check, csrf bootstrap.
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/users")
                    .permitAll()
                    .requestMatchers("/users/csrf", "/users/check-username", "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.bearerTokenResolver(cookieBearerTokenResolver).jwt {}
            }
        return http.build()
    }

    @Bean
    fun cookieBearerTokenResolver(): CookieBearerTokenResolver = CookieBearerTokenResolver(cookieName)
}
