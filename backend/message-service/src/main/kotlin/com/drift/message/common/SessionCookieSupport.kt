package com.drift.message.common

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration

/**
 * Session-cookie helpers (spec sections 2 & 19): the JWT lives only in the
 * `drift_session` cookie — HttpOnly, Secure, SameSite=Lax, Path=/, Max-Age = SESSION_TTL.
 */
object SessionCookieSupport {
    private const val SAME_SITE = "Lax"

    fun write(
        response: HttpServletResponse,
        cookieName: String,
        jwt: String,
        ttl: Duration,
        secure: Boolean,
    ) {
        // Written as a raw header so the SameSite attribute is guaranteed to appear
        // (Tomcat's Cookie.setAttribute is not always reflected by addCookie).
        response.addHeader(
            "Set-Cookie",
            "$cookieName=$jwt; Path=/; Max-Age=${ttl.toSeconds()}; SameSite=$SAME_SITE" +
                (if (secure) "; Secure" else "") +
                "; HttpOnly",
        )
    }

    fun clear(
        response: HttpServletResponse,
        cookieName: String,
        secure: Boolean,
    ) {
        response.addHeader(
            "Set-Cookie",
            "$cookieName=; Path=/; Max-Age=0; SameSite=$SAME_SITE" +
                (if (secure) "; Secure" else "") +
                "; HttpOnly",
        )
    }

    fun read(
        request: HttpServletRequest,
        cookieName: String,
    ): String? =
        request.cookies
            ?.firstOrNull { it.name == cookieName && it.value.isNotBlank() }
            ?.value
}
