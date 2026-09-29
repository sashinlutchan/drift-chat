package com.drift.room.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class JwtSupportTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val jwt = JwtSupport("test-secret-32-bytes-long!!!!!!!".toByteArray(), clock)

    @Test
    fun `issue then verify returns claims`() {
        val token = jwt.issue("user-1", "sipho", Duration.ofHours(24)).serialize()
        val claims = jwt.verify(token)
        assertThat(claims).isNotNull
        assertThat(claims!!.subject).isEqualTo("user-1")
        assertThat(claims.getClaim("username")).isEqualTo("sipho")
        assertThat(claims.expirationTime.toInstant()).isEqualTo(now.plus(Duration.ofHours(24)))
    }

    @Test
    fun `expired token fails verification`() {
        val token = jwt.issue("user-1", "sipho", Duration.ofSeconds(1)).serialize()
        val later = JwtSupport("test-secret-32-bytes-long!!!!!!!".toByteArray(), Clock.offset(clock, Duration.ofHours(1)))
        assertThat(later.verify(token)).isNull()
    }

    @Test
    fun `tampered token fails verification`() {
        val token = jwt.issue("user-1", "sipho", Duration.ofHours(24)).serialize()
        val tampered = token.dropLast(4) + "AAAA"
        assertThat(jwt.verify(tampered)).isNull()
    }

    @Test
    fun `wrong secret fails verification`() {
        val token = jwt.issue("user-1", "sipho", Duration.ofHours(24)).serialize()
        val other = JwtSupport("another-secret-32-bytes-long!!!!".toByteArray(), clock)
        assertThat(other.verify(token)).isNull()
    }

    @Test
    fun `garbage token fails verification`() {
        assertThat(jwt.verify("not-a-jwt")).isNull()
    }
}
