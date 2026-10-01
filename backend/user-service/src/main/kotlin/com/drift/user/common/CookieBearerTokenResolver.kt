package com.drift.user.common

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver

class CookieBearerTokenResolver(
    private val cookieName: String,
) : BearerTokenResolver {
    override fun resolve(request: HttpServletRequest): String? = SessionCookieSupport.read(request, cookieName)
}
