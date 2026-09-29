package com.drift.user.common

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import javax.crypto.spec.SecretKeySpec

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
    @Value("\${JWT_SECRET:test-secret-key-that-is-32-bytes-long!}") private val jwtSecret: String,
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
                    .requestMatchers("/users/csrf", "/users/check-username", "/error", "/actuator/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.exceptionHandling { it.authenticationEntryPoint(BearerTokenAuthenticationEntryPoint()) }
            // Wired by hand instead of `oauth2ResourceServer {}`: that DSL exempts every request carrying a
            // "bearer token" from CSRF, and our resolver reads the cookie — so CSRF would be off for all
            // signed-in requests. Doing it manually keeps the double-submit check in force.
            .addFilterBefore(bearerTokenFilter(cookieBearerTokenResolver), AuthorizationFilter::class.java)
        return http.build()
    }

    /** HS256 decoder for the session JWT (same secret as [JwtSupport]). */
    @Bean
    fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder
            .withSecretKey(SecretKeySpec(jwtSecret.toByteArray(), "HmacSHA256"))
            .macAlgorithm(MacAlgorithm.HS256)
            .build()

    private fun bearerTokenFilter(resolver: CookieBearerTokenResolver): BearerTokenAuthenticationFilter {
        val provider = JwtAuthenticationProvider(jwtDecoder())
        val filter = BearerTokenAuthenticationFilter(ProviderManager(provider))
        filter.setBearerTokenResolver(resolver)
        filter.setAuthenticationEntryPoint(BearerTokenAuthenticationEntryPoint())
        return filter
    }

    @Bean
    fun cookieBearerTokenResolver(): CookieBearerTokenResolver = CookieBearerTokenResolver(cookieName)
}
