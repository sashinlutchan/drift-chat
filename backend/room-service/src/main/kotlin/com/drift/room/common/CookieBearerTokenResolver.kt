package com.drift.room.common

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver

/**
 * Reads the JWT from the `drift_session` cookie instead of the `Authorization` header
 * (spec section 11: "custom BearerTokenResolver"). JavaScript never sees the token.
 */
class CookieBearerTokenResolver(
    private val cookieName: String,
) : BearerTokenResolver {
    override fun resolve(request: HttpServletRequest): String? = SessionCookieSupport.read(request, cookieName)
}
