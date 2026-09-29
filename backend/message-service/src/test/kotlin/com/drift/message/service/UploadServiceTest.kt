package com.drift.message.service

import com.drift.message.TestSupport
import com.drift.message.exceptions.DomainError
import com.drift.message.interfaces.IFileStore
import com.drift.message.interfaces.IMessageService
import com.drift.message.interfaces.IRoomMembership
import com.drift.message.interfaces.IUploadSessionStore
import com.drift.message.models.CompletedUpload
import com.drift.message.models.Message
import com.drift.message.models.MessageType
import com.drift.message.models.RoomAccess
import com.drift.message.models.StartedUpload
import com.drift.message.models.UploadSession
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.util.unit.DataSize
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.UUID

class UploadServiceTest {
    private val files = mockk<IFileStore>()
    private val sessions = mockk<IUploadSessionStore>()
    private val messages = mockk<IMessageService>()
    private val rooms = mockk<IRoomMembership>()
    private val meters = SimpleMeterRegistry()
    private val chunk = 5L * 1024 * 1024
    private val service =
        UploadService(
            files,
            sessions,
            messages,
            rooms,
            TestSupport.properties(chunk = DataSize.ofMegabytes(5), maxFile = DataSize.ofMegabytes(100)),
            meters,
        )

    private val me = UUID.randomUUID()
    private val room = UUID.randomUUID()
    private val fileId = UUID.randomUUID()

    private fun session(
        size: Long = 12L * 1024 * 1024,
        uploader: UUID = me,
    ) = UploadSession("up-1", room, uploader, "clip.mp4", size, "video/mp4", fileId, "mp-1", chunk, ((size + chunk - 1) / chunk).toInt())

    // ── start ───────────────────────────────────────────────────────────────

    @Test
    fun `starting checks the room, opens a multipart upload and stores the session`() {
        every { rooms.access(room, me) } returns RoomAccess.MEMBER
        every { files.start(me, room, "clip.mp4", "video/mp4", 12L * 1024 * 1024) } returns StartedUpload(fileId, "mp-1")
        val stored = slot<UploadSession>()
        every { sessions.create(capture(stored), any()) } just runs

        val started = service.start(me, room, "clip.mp4", 12L * 1024 * 1024, "Video/MP4")

        assertThat(started.totalChunks).isEqualTo(3)
        assertThat(started.chunkSize).isEqualTo(chunk)
        assertThat(stored.captured.multipartId).isEqualTo("mp-1")
        assertThat(stored.captured.uploaderId).isEqualTo(me)
        assertThat(meters.counter("drift.uploads.started").count()).isEqualTo(1.0)
    }

    @Test
    fun `a file exactly one chunk long is a single chunk`() {
        every { rooms.access(room, me) } returns RoomAccess.MEMBER
        every { files.start(any(), any(), any(), any(), any()) } returns StartedUpload(fileId, "mp-1")
        every { sessions.create(any(), any()) } just runs

        assertThat(service.start(me, room, "a.mp4", chunk, "video/mp4").totalChunks).isEqualTo(1)
    }

    @Test
    fun `path parts are stripped from the file name`() {
        every { rooms.access(room, me) } returns RoomAccess.MEMBER
        every { files.start(any(), any(), "evil.mp4", any(), any()) } returns StartedUpload(fileId, "mp-1")
        every { sessions.create(any(), any()) } just runs

        assertThat(service.start(me, room, "../../etc/evil.mp4", 100, "video/mp4").fileName).isEqualTo("evil.mp4")
    }

    @Test
    fun `starting is refused for non members, missing rooms, oversized and unsupported files`() {
        every { rooms.access(room, me) } returns RoomAccess.NOT_MEMBER
        assertThatThrownBy { service.start(me, room, "a.mp4", 10, "video/mp4") }.isInstanceOf(DomainError.Forbidden::class.java)

        every { rooms.access(room, me) } returns RoomAccess.NO_SUCH_ROOM
        assertThatThrownBy { service.start(me, room, "a.mp4", 10, "video/mp4") }.isInstanceOf(DomainError.NotFound::class.java)

        every { rooms.access(room, me) } returns RoomAccess.MEMBER
        assertThatThrownBy {
            service.start(me, room, "a.mp4", 101L * 1024 * 1024, "video/mp4")
        }.isInstanceOf(DomainError.PayloadTooLarge::class.java)
        assertThatThrownBy { service.start(me, room, "a.mp4", 0, "video/mp4") }.isInstanceOf(DomainError.ValidationFailed::class.java)
        assertThatThrownBy { service.start(me, room, "  ", 10, "video/mp4") }.isInstanceOf(DomainError.ValidationFailed::class.java)
        assertThatThrownBy {
            service.start(
                me,
                room,
                "a.zip",
                10,
                "application/zip",
            )
        }.isInstanceOf(DomainError.UnsupportedMediaType::class.java)
        verify(exactly = 0) { files.start(any(), any(), any(), any(), any()) }
    }

    // ── chunks ──────────────────────────────────────────────────────────────

    @Test
    fun `a chunk is streamed to file-service as part index+1 and recorded`() {
        every { sessions.find("up-1") } returns session()
        every { files.uploadPart(StartedUpload(fileId, "mp-1"), 2, any(), chunk) } returns "etag"
        every { sessions.markChunk("up-1", 1, any()) } just runs

        service.uploadChunk(me, "up-1", 1, chunk, ByteArrayInputStream(ByteArray(0)))

        verify { files.uploadPart(StartedUpload(fileId, "mp-1"), 2, any(), chunk) }
        verify { sessions.markChunk("up-1", 1, any()) }
    }

    @Test
    fun `the last chunk is shorter`() {
        val s = session()
        val last = s.sizeBytes - 2 * chunk
        every { sessions.find("up-1") } returns s
        every { files.uploadPart(any(), 3, any(), last) } returns "etag"
        every { sessions.markChunk(any(), any(), any()) } just runs

        service.uploadChunk(me, "up-1", 2, last, ByteArrayInputStream(ByteArray(0)))

        verify { files.uploadPart(any(), 3, any(), last) }
    }

    @Test
    fun `wrong-sized chunks and bad indexes are rejected before touching file-service`() {
        every { sessions.find("up-1") } returns session()

        assertThatThrownBy {
            service.uploadChunk(me, "up-1", 0, chunk - 1, ByteArrayInputStream(ByteArray(0)))
        }.isInstanceOf(DomainError.ValidationFailed::class.java)
        assertThatThrownBy {
            service.uploadChunk(me, "up-1", 2, chunk, ByteArrayInputStream(ByteArray(0)))
        }.isInstanceOf(DomainError.ValidationFailed::class.java)
        assertThatThrownBy {
            service.uploadChunk(
                me,
                "up-1",
                3,
                chunk,
                ByteArrayInputStream(ByteArray(0)),
            )
        }.hasMessageContaining("between 0 and 2")
        assertThatThrownBy {
            service.uploadChunk(me, "up-1", -1, chunk, ByteArrayInputStream(ByteArray(0)))
        }.isInstanceOf(DomainError.ValidationFailed::class.java)
        verify(exactly = 0) { files.uploadPart(any(), any(), any(), any()) }
    }

    @Test
    fun `nobody else can touch your upload, and expired uploads are gone`() {
        val stranger = UUID.randomUUID()
        every { sessions.find("up-1") } returns session()
        every { sessions.find("gone") } returns null

        assertThatThrownBy {
            service.uploadChunk(stranger, "up-1", 0, chunk, ByteArrayInputStream(ByteArray(0)))
        }.isInstanceOf(DomainError.Forbidden::class.java)
        assertThatThrownBy { service.status(stranger, "up-1") }.isInstanceOf(DomainError.Forbidden::class.java)
        assertThatThrownBy { service.complete(stranger, "up-1") }.isInstanceOf(DomainError.Forbidden::class.java)
        assertThatThrownBy { service.cancel(stranger, "up-1") }.isInstanceOf(DomainError.Forbidden::class.java)
        assertThatThrownBy { service.status(me, "gone") }.isInstanceOf(DomainError.NotFound::class.java)
    }

    // ── status / resume ─────────────────────────────────────────────────────

    @Test
    fun `status reports which chunks arrived so a client can resume`() {
        every { sessions.find("up-1") } returns session()
        every { sessions.received("up-1") } returns setOf(0, 2)

        val (s, received) = service.status(me, "up-1")

        assertThat(s.totalChunks).isEqualTo(3)
        assertThat(received).containsExactlyInAnyOrder(0, 2)
    }

    // ── complete ────────────────────────────────────────────────────────────

    @Test
    fun `completing assembles the file, creates the media message and clears the session`() {
        val s = session()
        val message = Message(UUID.randomUUID(), room, me, false, MessageType.VIDEO, "clip.mp4", fileId, Instant.EPOCH)
        every { sessions.find("up-1") } returns s
        every { sessions.received("up-1") } returns setOf(0, 1, 2)
        every { files.complete(StartedUpload(fileId, "mp-1"), s.sizeBytes) } returns CompletedUpload(fileId, "video/mp4", s.sizeBytes)
        every { messages.sendMedia(room, me, fileId, "clip.mp4", MessageType.VIDEO) } returns message
        every { sessions.delete("up-1") } just runs

        assertThat(service.complete(me, "up-1")).isEqualTo(message)

        verify { sessions.delete("up-1") }
        assertThat(meters.counter("drift.uploads.completed", "type", "video").count()).isEqualTo(1.0)
    }

    @Test
    fun `completing with chunks missing is a conflict that names them`() {
        every { sessions.find("up-1") } returns session()
        every { sessions.received("up-1") } returns setOf(0, 2)

        assertThatThrownBy { service.complete(me, "up-1") }.isInstanceOf(DomainError.Conflict::class.java).hasMessageContaining("[1]")
        verify(exactly = 0) { files.complete(any(), any()) }
    }

    @Test
    fun `when file-service rejects the finished file the session is discarded`() {
        every { sessions.find("up-1") } returns session()
        every { sessions.received("up-1") } returns setOf(0, 1, 2)
        every { files.complete(any(), any()) } throws DomainError.ValidationFailed("wrong type")
        every { sessions.delete("up-1") } just runs

        assertThatThrownBy { service.complete(me, "up-1") }.isInstanceOf(DomainError.ValidationFailed::class.java)
        verify { sessions.delete("up-1") }
        verify(exactly = 0) { messages.sendMedia(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a finished file of an unusable type is refused`() {
        val s = session()
        every { sessions.find("up-1") } returns s
        every { sessions.received("up-1") } returns setOf(0, 1, 2)
        every { files.complete(any(), any()) } returns CompletedUpload(fileId, "application/zip", s.sizeBytes)

        assertThatThrownBy { service.complete(me, "up-1") }.isInstanceOf(DomainError.UnsupportedMediaType::class.java)
    }

    // ── cancel ──────────────────────────────────────────────────────────────

    @Test
    fun `cancelling aborts the multipart upload and forgets the session, and cancelling twice is fine`() {
        every { sessions.find("up-1") } returnsMany listOf(session(), null)
        every { files.abort(StartedUpload(fileId, "mp-1")) } just runs
        every { sessions.delete("up-1") } just runs

        service.cancel(me, "up-1")
        service.cancel(me, "up-1")

        verify(exactly = 1) { files.abort(any()) }
    }
}
