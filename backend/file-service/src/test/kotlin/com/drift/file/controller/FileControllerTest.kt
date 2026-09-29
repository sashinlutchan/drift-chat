package com.drift.file.controller

import com.drift.file.common.JwtSupport
import com.drift.file.common.SecurityBeans
import com.drift.file.config.AppProperties
import com.drift.file.exceptions.DomainError
import com.drift.file.interfaces.IFileService
import com.drift.file.models.ByteRange
import com.drift.file.models.FileContent
import com.drift.file.models.FileKind
import com.drift.file.models.FileRecord
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [FileController::class])
@Import(SecurityBeans::class)
// A developer's local .env must never change what the tests sign and verify with
@TestPropertySource(properties = ["JWT_SECRET=test-secret-key-that-is-32-bytes-long!"])
class FileControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @MockkBean lateinit var files: IFileService

    @MockkBean(relaxed = true)
    lateinit var appProperties: AppProperties

    private val userId = UUID.randomUUID()
    private val fileId = UUID.randomUUID()
    private val bytes = ByteArray(1000) { it.toByte() }

    private fun session() =
        Cookie(
            "drift_session",
            JwtSupport("test-secret-key-that-is-32-bytes-long!".toByteArray(), Clock.systemUTC())
                .issue(userId.toString(), "sipho", Duration.ofHours(1))
                .serialize(),
        )

    private fun record() = FileRecord(fileId, userId, UUID.randomUUID(), FileKind.MEDIA, "video/mp4", 1000, "media/x", Instant.EPOCH)

    private fun content(
        range: ByteRange,
        partial: Boolean,
    ) = FileContent(record(), range, partial) { bytes.copyOfRange(range.start.toInt(), range.end.toInt() + 1).inputStream() }

    @Test
    fun `no session cookie is 401`() {
        mockMvc.get("/files/$fileId").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `full download returns 200 with type, length and range support advertised`() {
        every { files.open(fileId, userId, null) } returns content(ByteRange(0, 999), false)

        val result = mockMvc.get("/files/$fileId") { cookie(session()) }.andExpect { request { asyncStarted() } }.andReturn()

        mockMvc
            .perform(asyncDispatch(result))
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .status()
                    .isOk,
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .header()
                    .string("Accept-Ranges", "bytes"),
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .header()
                    .string("Content-Type", "video/mp4"),
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .header()
                    .string("X-Content-Type-Options", "nosniff"),
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .content()
                    .bytes(bytes),
            )
    }

    @Test
    fun `range request returns 206 with Content-Range and only those bytes`() {
        every { files.open(fileId, userId, "bytes=100-199") } returns content(ByteRange(100, 199), true)

        val result =
            mockMvc
                .get("/files/$fileId") {
                    cookie(session())
                    header("Range", "bytes=100-199")
                }.andExpect { request { asyncStarted() } }
                .andReturn()

        mockMvc
            .perform(asyncDispatch(result))
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .status()
                    .isPartialContent,
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .header()
                    .string("Content-Range", "bytes 100-199/1000"),
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .content()
                    .bytes(bytes.copyOfRange(100, 200)),
            )
    }

    @Test
    fun `non member is 403 with a problem detail code`() {
        every { files.open(fileId, userId, null) } throws DomainError.Forbidden("You are not a member of this room.")

        mockMvc
            .get("/files/$fileId") { cookie(session()) }
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("FORBIDDEN") }
            }
    }

    @Test
    fun `unknown file is 404 and unsatisfiable range is 416`() {
        every { files.open(fileId, userId, null) } throws DomainError.NotFound("File not found.")
        mockMvc.get("/files/$fileId") { cookie(session()) }.andExpect { status { isNotFound() } }

        every { files.open(fileId, userId, "bytes=9999-") } throws DomainError.RangeNotSatisfiable("nope")
        mockMvc
            .get("/files/$fileId") {
                cookie(session())
                header("Range", "bytes=9999-")
            }.andExpect { status { isEqualTo(416) } }
    }

    @Test
    fun `a tampered cookie is 401`() {
        mockMvc.get("/files/$fileId") { cookie(Cookie("drift_session", "nope")) }.andExpect { status { isUnauthorized() } }
    }
}
