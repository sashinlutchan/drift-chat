package com.drift.room.controller

import com.drift.room.common.JwtSupport
import com.drift.room.common.SecurityBeans
import com.drift.room.config.AppProperties
import com.drift.room.exceptions.DomainError
import com.drift.room.interfaces.IRoomService
import com.drift.room.models.MemberProfile
import com.drift.room.models.Room
import com.drift.room.models.RoomInfo
import com.drift.room.models.RoomSummary
import com.drift.room.models.UserProfile
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [RoomController::class])
@Import(SecurityBeans::class)
@TestPropertySource(properties = ["JWT_SECRET=test-secret-key-that-is-32-bytes-long!"])
class RoomControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @MockkBean lateinit var rooms: IRoomService

    @MockkBean lateinit var properties: AppProperties

    private val userId = UUID.randomUUID()
    private val room = Room(UUID.randomUUID(), "Movies", "movies-abc123", userId, Instant.EPOCH, Instant.EPOCH, null)
    private val summary = RoomSummary(room, 3, 2, true, false, null)

    private fun session() =
        Cookie(
            "drift_session",
            JwtSupport("test-secret-key-that-is-32-bytes-long!".toByteArray(), Clock.systemUTC())
                .issue(userId.toString(), "sipho", Duration.ofHours(1))
                .serialize(),
        )

    private fun stubProperties() {
        every { properties.publicBaseUrl } returns "https://drift.test"
    }

    @Test
    fun `no session is 401`() {
        mockMvc.get("/rooms").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `list returns cards with counts and a share url`() {
        stubProperties()
        every { rooms.list(userId, "mov") } returns listOf(summary)

        mockMvc
            .get("/rooms?search=mov") { cookie(session()) }
            .andExpect {
                status { isOk() }
                jsonPath("$[0].name") { value("Movies") }
                jsonPath("$[0].memberCount") { value(3) }
                jsonPath("$[0].onlineCount") { value(2) }
                jsonPath("$[0].joined") { value(true) }
                jsonPath("$[0].pinned") { value(false) }
                jsonPath("$[0].shareUrl") { value("https://drift.test/r/movies-abc123") }
            }
    }

    @Test
    fun `create without the csrf token is forbidden`() {
        mockMvc
            .post("/rooms") {
                cookie(session())
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Movies"}"""
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `create with a valid csrf token returns 201`() {
        stubProperties()
        every { rooms.create(userId, "Movies") } returns summary

        mockMvc
            .post("/rooms") {
                cookie(session())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Movies"}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.slug") { value("movies-abc123") }
            }
    }

    @Test
    fun `create validates the name with a problem detail`() {
        mockMvc
            .post("/rooms") {
                cookie(session())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
            }
    }

    @Test
    fun `room info includes the creator, or null when the creator was deleted`() {
        stubProperties()
        val creator = UserProfile(userId, "sipho", "Sipho", null, true)
        every { rooms.info(room.id, userId) } returns RoomInfo(summary, creator, null)

        mockMvc
            .get("/rooms/${room.id}") { cookie(session()) }
            .andExpect {
                status { isOk() }
                jsonPath("$.createdBy.name") { value("Sipho") }
                jsonPath("$.room.name") { value("Movies") }
            }

        every { rooms.info(room.id, userId) } returns RoomInfo(summary, null, Instant.parse("2026-06-02T00:00:00Z"))
        mockMvc
            .get("/rooms/${room.id}") { cookie(session()) }
            .andExpect {
                jsonPath("$.createdBy") { value(null) }
                jsonPath("$.idleDeleteAt") { exists() }
            }
    }

    @Test
    fun `a deleted room is 404 with a code`() {
        every { rooms.bySlug("gone", userId) } throws DomainError.NotFound("This room no longer exists.")

        mockMvc
            .get("/rooms/by-slug/gone") { cookie(session()) }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.detail") { value("This room no longer exists.") }
            }
    }

    @Test
    fun `by slug resolves a share link`() {
        stubProperties()
        every { rooms.bySlug("movies-abc123", userId) } returns summary

        mockMvc.get("/rooms/by-slug/movies-abc123") { cookie(session()) }.andExpect { status { isOk() } }
    }

    @Test
    fun `members list has online flag and avatar`() {
        every { rooms.members(room.id) } returns listOf(MemberProfile(userId, "sipho", "Sipho", null, true))

        mockMvc
            .get("/rooms/${room.id}/members") { cookie(session()) }
            .andExpect {
                status { isOk() }
                jsonPath("$[0].online") { value(true) }
                jsonPath("$[0].name") { value("Sipho") }
            }
    }

    @Test
    fun `join, leave, pin and unpin work with csrf and are forbidden without`() {
        stubProperties()
        every { rooms.join(room.id, userId) } returns summary
        every { rooms.leave(room.id, userId) } just runs
        every { rooms.pin(room.id, userId) } just runs
        every { rooms.unpin(room.id, userId) } just runs

        mockMvc
            .post("/rooms/${room.id}/join") {
                cookie(session())
                with(csrf())
            }.andExpect { status { isOk() } }
        mockMvc
            .post("/rooms/${room.id}/leave") {
                cookie(session())
                with(csrf())
            }.andExpect { status { isNoContent() } }
        mockMvc
            .put("/rooms/${room.id}/pin") {
                cookie(session())
                with(csrf())
            }.andExpect { status { isNoContent() } }
        mockMvc
            .delete("/rooms/${room.id}/pin") {
                cookie(session())
                with(csrf())
            }.andExpect { status { isNoContent() } }

        mockMvc.post("/rooms/${room.id}/join") { cookie(session()) }.andExpect { status { isForbidden() } }
        mockMvc.put("/rooms/${room.id}/pin") { cookie(session()) }.andExpect { status { isForbidden() } }
        mockMvc.delete("/rooms/${room.id}/pin") { cookie(session()) }.andExpect { status { isForbidden() } }
        verify(exactly = 1) { rooms.pin(room.id, userId) }
    }

    @Test
    fun `a tampered cookie is 401`() {
        mockMvc.get("/rooms") { cookie(Cookie("drift_session", "nope")) }.andExpect { status { isUnauthorized() } }
    }
}
