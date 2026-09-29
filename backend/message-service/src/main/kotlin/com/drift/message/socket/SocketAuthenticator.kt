package com.drift.message.socket

import com.drift.message.common.JwtSupport
import com.drift.message.config.AppProperties
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/** The outcome of checking a Socket.IO handshake. */
sealed interface SocketAuth {
    data class Accepted(
        val userId: UUID,
        val expiresAt: Instant,
    ) : SocketAuth

    data class Rejected(
        val reason: String,
    ) : SocketAuth
}

/**
 * Checks a WebSocket handshake before any connection exists (spec sections 13.3 and 19): the `Origin` must be on the
 * allow-list (protection against cross-site WebSocket hijacking) and the `drift_session` cookie must hold a valid,
 * unexpired JWT. Nothing goes in the Socket.IO `auth` payload.
 */
@Component
class SocketAuthenticator(
    private val jwt: JwtSupport,
    private val properties: AppProperties,
) {
    fun authenticate(
        origin: String?,
        cookieHeader: String?,
    ): SocketAuth {
        val allowed = properties.socket.allowedOrigins.map { it.trim().trimEnd('/') }
        if (origin == null || origin.trimEnd('/') !in allowed) return SocketAuth.Rejected("origin not allowed")

        val token = cookie(cookieHeader, properties.sessionCookieName) ?: return SocketAuth.Rejected("no session cookie")
        val claims = jwt.verify(token) ?: return SocketAuth.Rejected("invalid or expired session")
        val userId = runCatching { UUID.fromString(claims.subject) }.getOrNull() ?: return SocketAuth.Rejected("invalid session")
        return SocketAuth.Accepted(userId, claims.expirationTime.toInstant())
    }

    private fun cookie(
        header: String?,
        name: String,
    ): String? =
        header
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
}
