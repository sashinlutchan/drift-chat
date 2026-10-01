package com.drift.user.controller

import com.drift.user.common.JwtSupport
import com.drift.user.config.AppProperties
import com.drift.user.exceptions.DomainError
import com.drift.user.models.User
import com.drift.user.service.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [UserController::class])
@org.springframework.context.annotation.Import(com.drift.user.common.SecurityBeans::class)
@AutoConfigureMockMvc(addFilters = true)
@org.springframework.test.context.TestPropertySource(
    properties = [
        "JWT_SECRET=test-secret-key-that-is-32-bytes-long!",
        "drift.jwt-secret=test-secret-32-bytes-long!!!!!!!",
        "drift.session-cookie-name=drift_session",
        "drift.cookie-secure=true",
        "drift.session-ttl=24h",
        "drift.presence-ttl=60s",
        "drift.cleanup-interval=5m",
        "drift.max-avatar-size=5MB",
        "drift.file-service-addr=localhost:9084",
    ],
)
class UserControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @MockkBean lateinit var userService: UserService

    @MockkBean(relaxed = true)
    lateinit var appProperties: AppProperties

    private val userId = UUID.randomUUID()

    private fun user() =
        User(userId, "sipho", "Sipho", "s@b.co", UUID.randomUUID(), Instant.EPOCH, Instant.EPOCH, Instant.EPOCH.plusSeconds(86400))

    @BeforeEach
    fun stubPresence() {
        every { userService.isOnline(any()) } returns true
    }

    @Test
    fun `check username returns availability`() {
        every { userService.usernameAvailable("sipho") } returns true

        mockMvc.get("/users/check-username?username=sipho").andExpect {
            status { isOk() }
            jsonPath("$.available") { value(true) }
        }
        assertThat(true).isTrue()
    }

    @Test
    fun `public profile hides email`() {
        every { userService.getById(userId) } returns user()

        mockMvc
            .get("/users/$userId") {
                with(SecurityMockMvcRequestPostProcessors.jwt())
            }.andExpect {
                status { isOk() }
                jsonPath("$.username") { value("sipho") }
                jsonPath("$.email") { doesNotExist() }
            }
    }

    @Test
    fun `public profile of missing user returns problem detail with code`() {
        every { userService.getById(userId) } throws DomainError.NotFound("User not found.")

        mockMvc
            .get("/users/$userId") {
                with(SecurityMockMvcRequestPostProcessors.jwt())
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("NOT_FOUND") }
            }
    }

    @Test
    fun `sign up without csrf token is rejected with 403`() {
        val avatar = MockMultipartFile("avatar", "a.png", "image/png", byteArrayOf(1))
        val username = MockMultipartFile("username", "", MediaType.TEXT_PLAIN_VALUE, "sipho".toByteArray())
        val name = MockMultipartFile("name", "", MediaType.TEXT_PLAIN_VALUE, "Sipho".toByteArray())
        val email = MockMultipartFile("email", "", MediaType.TEXT_PLAIN_VALUE, "s@b.co".toByteArray())

        mockMvc
            .multipart("/users") {
                file(avatar)
                file(username)
                file(name)
                file(email)
            }.andExpect { status { isForbidden() } }
    }

    private fun sessionCookie(): Cookie =
        Cookie(
            "drift_session",
            JwtSupport("test-secret-key-that-is-32-bytes-long!".toByteArray(), Clock.systemUTC())
                .issue(userId.toString(), "sipho", Duration.ofHours(1))
                .serialize(),
        )

    @Test
    fun `patch profile with a valid session cookie but no csrf token is rejected with 403`() {
        mockMvc
            .multipart(HttpMethod.PATCH, "/users/me") {
                file(MockMultipartFile("name", "", MediaType.TEXT_PLAIN_VALUE, "New".toByteArray()))
                cookie(sessionCookie())
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `me with a real signed session cookie is authenticated`() {
        every { userService.getById(userId) } returns user()

        mockMvc
            .get("/users/me") { cookie(sessionCookie()) }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `me with a tampered session cookie returns 401`() {
        mockMvc
            .get("/users/me") { cookie(Cookie("drift_session", "not-a-jwt")) }
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `the csrf bootstrap endpoint is public and sets a readable XSRF-TOKEN cookie`() {
        mockMvc
            .get("/users/csrf")
            .andExpect {
                status { isOk() }
                cookie { exists("XSRF-TOKEN") }
                cookie { httpOnly("XSRF-TOKEN", false) }
            }
    }

    @Test
    fun `me without session cookie returns 401`() {
        mockMvc.get("/users/me").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `me with jwt principal returns own profile with sessionExpiresAt`() {
        every { userService.getById(userId) } returns user()
        val jwt =
            Jwt
                .withTokenValue("t")
                .subject(userId.toString())
                .header("alg", "HS256")
                .build()
        val auth = SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)

        mockMvc
            .get("/users/me") {
                with(auth)
            }.andExpect {
                status { isOk() }
                jsonPath("$.sessionExpiresAt") { exists() }
                jsonPath("$.email") { value("s@b.co") }
            }
    }
}
