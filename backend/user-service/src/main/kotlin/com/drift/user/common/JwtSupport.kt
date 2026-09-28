package com.drift.user.common

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Date

/**
 * JWT (HS256) issue/verify helpers (spec section 19).
 * The token carries the user id as `sub`, the username as claim `username`,
 * and `exp` = issue time + session TTL. It only ever travels in the session cookie.
 */
class JwtSupport(
    private val secret: ByteArray,
    private val clock: Clock,
) {
    init {
        require(secret.size >= MIN_SECRET_BYTES) { "JWT_SECRET must be at least 32 bytes for HS256" }
    }

    companion object {
        const val MIN_SECRET_BYTES = 32
    }

    fun issue(
        userId: String,
        username: String,
        ttl: Duration,
    ): SignedJWT {
        val now = Instant.now(clock)
        val claims =
            JWTClaimsSet
                .Builder()
                .subject(userId)
                .claim("username", username)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.HS256).build(), claims)
        jwt.sign(MACSigner(secret))
        return jwt
    }

    /** Verifies signature and expiry; returns the claims or `null` when invalid/expired. */
    fun verify(token: String): JWTClaimsSet? =
        try {
            val jwt = SignedJWT.parse(token)
            val notExpired = jwt.jwtClaimsSet.expirationTime.after(Date.from(Instant.now(clock)))
            if (jwt.verify(MACVerifier(secret)) && notExpired) {
                jwt.jwtClaimsSet
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
}
