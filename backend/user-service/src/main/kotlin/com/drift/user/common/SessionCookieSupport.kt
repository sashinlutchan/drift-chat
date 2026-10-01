package com.drift.user.common

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration

object SessionCookieSupport {
    private const val SAME_SITE = "Lax"

    fun write(
        response: HttpServletResponse,
        cookieName: String,
        jwt: String,
        ttl: Duration,
        secure: Boolean,
    ) {
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
