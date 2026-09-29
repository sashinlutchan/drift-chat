package com.drift.room.common

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Duration

class SessionCookieSupportTest {
    private val response: HttpServletResponse = MockHttpServletResponse()
    private val request: HttpServletRequest = MockHttpServletRequest()

    @Test
    fun `write sets HttpOnly Secure SameSite Lax Path and Max-Age`() {
        SessionCookieSupport.write(response, "drift_session", "jwt-token", Duration.ofHours(24), secure = true)

        val cookie = response.getHeader("Set-Cookie")
        assertThat(cookie).contains("drift_session=jwt-token")
        assertThat(cookie).contains("HttpOnly")
        assertThat(cookie).contains("Secure")
        assertThat(cookie).contains("SameSite=Lax")
        assertThat(cookie).contains("Path=/")
        assertThat(cookie).contains("Max-Age=86400")
    }

    @Test
    fun `write without secure omits Secure flag`() {
        SessionCookieSupport.write(response, "drift_session", "jwt-token", Duration.ofHours(1), secure = false)
        assertThat(response.getHeader("Set-Cookie")).doesNotContain("Secure")
    }

    @Test
    fun `clear expires the cookie`() {
        SessionCookieSupport.clear(response, "drift_session", secure = true)
        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0")
    }

    @Test
    fun `read returns the cookie value`() {
        val req = MockHttpServletRequest()
        req.setCookies(Cookie("drift_session", "the-jwt"), Cookie("other", "x"))
        assertThat(SessionCookieSupport.read(req, "drift_session")).isEqualTo("the-jwt")
    }

    @Test
    fun `read returns null when cookie missing`() {
        assertThat(SessionCookieSupport.read(MockHttpServletRequest(), "drift_session")).isNull()
    }

    @Test
    fun `read returns null for blank value`() {
        val req = MockHttpServletRequest()
        req.setCookies(Cookie("drift_session", ""))
        assertThat(SessionCookieSupport.read(req, "drift_session")).isNull()
    }
}
