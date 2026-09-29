package com.drift.message.controller

import com.drift.message.common.JwtSupport
import com.drift.message.common.SecurityBeans
import com.drift.message.config.AppProperties
import com.drift.message.dto.MessageDto
import com.drift.message.dto.SenderDto
import com.drift.message.exceptions.DomainError
import com.drift.message.interfaces.IMessageRenderer
import com.drift.message.interfaces.IMessageService
import com.drift.message.interfaces.IUploadService
import com.drift.message.models.Message
import com.drift.message.models.MessagePage
import com.drift.message.models.MessageType
import com.drift.message.models.UploadSession
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
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
import java.io.InputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [MessageController::class, UploadController::class])
@Import(SecurityBeans::class)
// A developer's local .env must never change what the tests sign and verify with
@TestPropertySource(properties = ["JWT_SECRET=test-secret-key-that-is-32-bytes-long!"])
class MessageControllersTest {
    @Autowired lateinit var mockMvc: MockMvc

    @MockkBean lateinit var messages: IMessageService

    @MockkBean lateinit var uploads: IUploadService

    @MockkBean lateinit var renderer: IMessageRenderer

    @MockkBean(relaxed = true)
    lateinit var properties: AppProperties

    private val userId = UUID.randomUUID()
    private val room = UUID.randomUUID()
    private val message =
        Message(UUID.randomUUID(), room, userId, false, MessageType.TEXT, "hello", null, Instant.parse("2026-06-01T12:00:00Z"))
    private val dto =
        MessageDto(message.id, room, "text", "hello", null, SenderDto(userId, "Sipho", "sipho", null, false), message.createdAt)

    private fun session() =
        Cookie(
            "drift_session",
            JwtSupport("test-secret-key-that-is-32-bytes-long!".toByteArray(), Clock.systemUTC())
                .issue(userId.toString(), "sipho", Duration.ofHours(1))
                .serialize(),
        )

    private val upload = UploadSession("up-1", room, userId, "clip.mp4", 12_000_000, "video/mp4", UUID.randomUUID(), "mp-1", 5_242_880, 3)

    // ── history ─────────────────────────────────────────────────────────────

    @Test
    fun `history needs a session`() {
        mockMvc.get("/messages?roomId=$room").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `history returns a page of messages with senders and the next cursor`() {
        val next = UUID.randomUUID()
        val before = UUID.randomUUID()
        every { messages.history(userId, room, before, 20) } returns MessagePage(listOf(message), next)
        every { renderer.render(listOf(message)) } returns listOf(dto)

        mockMvc
            .get("/messages?roomId=$room&before=$before&limit=20") { cookie(session()) }
            .andExpect {
                status { isOk() }
                jsonPath("$.messages[0].text") { value("hello") }
                jsonPath("$.messages[0].sender.name") { value("Sipho") }
                jsonPath("$.nextCursor") { value(next.toString()) }
            }
    }

    @Test
    fun `history of a room you are not in is 403 with a code`() {
        every { messages.history(userId, room, null, null) } throws DomainError.Forbidden("Join this room to read its messages.")

        mockMvc
            .get("/messages?roomId=$room") { cookie(session()) }
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("FORBIDDEN") }
            }
    }

    // ── uploads ─────────────────────────────────────────────────────────────

    @Test
    fun `starting an upload needs the csrf token and returns the chunk plan`() {
        every { uploads.start(userId, room, "clip.mp4", 12_000_000, "video/mp4") } returns upload
        val body = """{"roomId":"$room","fileName":"clip.mp4","size":12000000,"mimeType":"video/mp4"}"""

        mockMvc
            .post("/messages/uploads") {
                cookie(session())
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isForbidden() } }
        mockMvc
            .post("/messages/uploads") {
                cookie(session())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isCreated() }
                jsonPath("$.uploadId") { value("up-1") }
                jsonPath("$.totalChunks") { value(3) }
                jsonPath("$.chunkSize") { value(5_242_880) }
            }
    }

    @Test
    fun `starting an upload validates the request`() {
        mockMvc
            .post("/messages/uploads") {
                cookie(session())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomId":"$room","fileName":"","size":0,"mimeType":""}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
            }
    }

    @Test
    fun `a chunk body is streamed to the service with its length, never buffered here`() {
        val length = slot<Long>()
        var received = ByteArray(0)
        every { uploads.uploadChunk(userId, "up-1", 1, capture(length), any<InputStream>()) } answers {
            received = (invocation.args[4] as InputStream).readAllBytes()
        }

        mockMvc
            .put("/messages/uploads/up-1/chunks/1") {
                cookie(session())
                with(csrf())
                contentType = MediaType.APPLICATION_OCTET_STREAM
                content = ByteArray(1234) { 7 }
            }.andExpect { status { isNoContent() } }

        org.assertj.core.api.Assertions
            .assertThat(length.captured)
            .isEqualTo(1234)
        org.assertj.core.api.Assertions
            .assertThat(received)
            .hasSize(1234)
    }

    @Test
    fun `a chunk without the csrf token is forbidden`() {
        mockMvc
            .put("/messages/uploads/up-1/chunks/1") {
                cookie(session())
                contentType = MediaType.APPLICATION_OCTET_STREAM
                content = ByteArray(10)
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `status lists received and missing chunks`() {
        every { uploads.status(userId, "up-1") } returns (upload to setOf(0, 2))

        mockMvc
            .get("/messages/uploads/up-1") { cookie(session()) }
            .andExpect {
                status { isOk() }
                jsonPath("$.received[0]") { value(0) }
                jsonPath("$.received[1]") { value(2) }
                jsonPath("$.missing[0]") { value(1) }
                jsonPath("$.size") { value(12_000_000) }
            }
    }

    @Test
    fun `complete returns the created media message, and missing chunks are 409`() {
        val media = message.copy(type = MessageType.VIDEO, fileId = UUID.randomUUID(), text = "clip.mp4")
        every { uploads.complete(userId, "up-1") } returns media
        every { renderer.render(listOf(media)) } returns listOf(dto.copy(type = "video", fileId = media.fileId))

        mockMvc
            .post("/messages/uploads/up-1/complete") {
                cookie(session())
                with(csrf())
            }.andExpect {
                status { isOk() }
                jsonPath("$.type") { value("video") }
            }

        every { uploads.complete(userId, "up-2") } throws DomainError.Conflict("Missing chunks: [1]")
        mockMvc
            .post("/messages/uploads/up-2/complete") {
                cookie(session())
                with(csrf())
            }.andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("CONFLICT") }
            }
    }

    @Test
    fun `cancelling an upload is 204`() {
        every { uploads.cancel(userId, "up-1") } just runs

        mockMvc
            .delete("/messages/uploads/up-1") {
                cookie(session())
                with(csrf())
            }.andExpect { status { isNoContent() } }
        verify { uploads.cancel(userId, "up-1") }
    }

    @Test
    fun `an unknown or expired upload is 404`() {
        every { uploads.status(userId, "gone") } throws DomainError.NotFound("This upload doesn't exist or has expired.")

        mockMvc.get("/messages/uploads/gone") { cookie(session()) }.andExpect { status { isNotFound() } }
    }
}
