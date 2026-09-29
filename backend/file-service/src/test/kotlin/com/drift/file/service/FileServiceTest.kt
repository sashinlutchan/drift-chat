package com.drift.file.service

import com.drift.file.config.AppProperties
import com.drift.file.exceptions.DomainError
import com.drift.file.interfaces.IMimeSniffer
import com.drift.file.interfaces.IObjectStorage
import com.drift.file.interfaces.IRoomMembership
import com.drift.file.models.FileKind
import com.drift.file.models.FileRecord
import com.drift.file.models.NewFile
import com.drift.file.models.PartEtag
import com.drift.file.models.UploadInfo
import com.drift.file.repository.IFileRepository
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
import java.time.Instant
import java.util.UUID

class FileServiceTest {
    private val repository = mockk<IFileRepository>()
    private val storage = mockk<IObjectStorage>(relaxed = true)
    private val sniffer = mockk<IMimeSniffer>()
    private val rooms = mockk<IRoomMembership>()
    private val meters = SimpleMeterRegistry()
    private val properties =
        AppProperties(
            jwtSecret = "x".repeat(32),
            sessionCookieName = "drift_session",
            maxAvatarSize = DataSize.ofKilobytes(10),
            roomServiceAddr = "localhost:9082",
            storage =
                AppProperties.Storage(
                    minioEndpoint = "http://minio",
                    minioAccessKey = "a",
                    minioSecretKey = "b",
                    minioBucketAvatars = "avatars",
                    minioBucketMedia = "media",
                    maxFileSize = DataSize.ofMegabytes(100),
                    abandonedUploadAge = java.time.Duration.ofDays(2),
                    allowedMimeTypes = listOf("image/png", "image/jpeg", "audio/mpeg", "video/mp4", "audio/wav"),
                ),
        )
    private val now = Instant.parse("2026-06-10T12:00:00Z")
    private val service =
        FileService(repository, storage, sniffer, rooms, properties, meters, java.time.Clock.fixed(now, java.time.ZoneOffset.UTC))

    private val owner = UUID.randomUUID()
    private val room = UUID.randomUUID()
    private val avatarInfo = UploadInfo(owner, null, FileKind.AVATAR, "a.png", "image/png")
    private val mediaInfo = UploadInfo(owner, room, FileKind.MEDIA, "v.mp4", "video/mp4")

    private fun record(
        kind: FileKind = FileKind.MEDIA,
        size: Long = 1000,
        roomId: UUID? = room,
    ) = FileRecord(UUID.randomUUID(), owner, roomId, kind, "video/mp4", size, "media/$roomId/x", Instant.EPOCH)

    private fun stubSave() {
        every { repository.save(any()) } answers {
            val f = firstArg<NewFile>()
            FileRecord(f.id, f.ownerId, f.roomId, f.kind, f.mimeType, f.sizeBytes, f.objectKey, Instant.EPOCH)
        }
    }

    // ── small uploads ───────────────────────────────────────────────────────

    @Test
    fun `avatar upload sniffs the type, stores the bytes and records the file`() {
        every { sniffer.detect(any()) } returns "image/png"
        stubSave()

        val stored = service.upload(avatarInfo, ByteArray(500))

        assertThat(stored.mimeType).isEqualTo("image/png")
        assertThat(stored.sizeBytes).isEqualTo(500)
        verify { storage.put(match { it.bucket == "avatars" }, any(), "image/png") }
        assertThat(meters.counter("drift.files.uploaded", "kind", "avatar").count()).isEqualTo(1.0)
    }

    @Test
    fun `the declared type is ignored - a non-image is rejected as an avatar`() {
        every { sniffer.detect(any()) } returns "application/x-sh"

        assertThatThrownBy { service.upload(avatarInfo, ByteArray(50)) }.isInstanceOf(DomainError.UnsupportedMediaType::class.java)
        verify(exactly = 0) { storage.put(any(), any(), any()) }
    }

    @Test
    fun `a video is not a valid avatar even if allowed for media`() {
        every { sniffer.detect(any()) } returns "video/mp4"
        assertThatThrownBy { service.upload(avatarInfo, ByteArray(50)) }.isInstanceOf(DomainError.UnsupportedMediaType::class.java)
    }

    @Test
    fun `oversized and empty avatars are rejected before touching storage`() {
        assertThatThrownBy { service.upload(avatarInfo, ByteArray(20_000)) }.isInstanceOf(DomainError.PayloadTooLarge::class.java)
        assertThatThrownBy { service.upload(avatarInfo, ByteArray(0)) }.isInstanceOf(DomainError.ValidationFailed::class.java)
        verify(exactly = 0) { storage.put(any(), any(), any()) }
    }

    @Test
    fun `if saving the record fails the stored object is removed again`() {
        every { sniffer.detect(any()) } returns "image/png"
        every { repository.save(any()) } throws IllegalStateException("db down")

        assertThatThrownBy { service.upload(avatarInfo, ByteArray(50)) }.isInstanceOf(IllegalStateException::class.java)
        verify { storage.delete(any()) }
    }

    // ── multipart ───────────────────────────────────────────────────────────

    @Test
    fun `start multipart validates the request and opens a MinIO upload`() {
        every { storage.startMultipart(any(), "video/mp4") } returns "mp-1"
        stubSave()

        val started = service.startMultipart(mediaInfo, 50_000_000)

        assertThat(started.multipartId).isEqualTo("mp-1")
        verify { storage.startMultipart(match { it.bucket == "media" }, "video/mp4") }
    }

    @Test
    fun `start multipart rejects bad requests`() {
        assertThatThrownBy { service.startMultipart(avatarInfo, 10) }.isInstanceOf(DomainError.ValidationFailed::class.java)
        assertThatThrownBy {
            service.startMultipart(
                mediaInfo.copy(roomId = null),
                10,
            )
        }.isInstanceOf(DomainError.ValidationFailed::class.java)
        assertThatThrownBy { service.startMultipart(mediaInfo, 0) }.isInstanceOf(DomainError.ValidationFailed::class.java)
        assertThatThrownBy { service.startMultipart(mediaInfo, 200L * 1024 * 1024) }.isInstanceOf(DomainError.PayloadTooLarge::class.java)
        assertThatThrownBy { service.startMultipart(mediaInfo.copy(declaredMimeType = "application/zip"), 10) }
            .isInstanceOf(DomainError.UnsupportedMediaType::class.java)
    }

    @Test
    fun `upload part sends the bytes to MinIO and returns the etag`() {
        val rec = record()
        every { repository.findById(rec.id) } returns rec
        every { storage.uploadPart(any(), "mp-1", 3, any()) } returns "etag-3"

        assertThat(service.uploadPart(rec.id, "mp-1", 3, ByteArray(10))).isEqualTo(PartEtag(3, "etag-3"))
    }

    @Test
    fun `upload part validates part number, size and file`() {
        val rec = record()
        every { repository.findById(rec.id) } returns rec
        every { repository.findById(neq(rec.id)) } returns null

        assertThatThrownBy { service.uploadPart(rec.id, "m", 0, ByteArray(1)) }.isInstanceOf(DomainError.ValidationFailed::class.java)
        assertThatThrownBy { service.uploadPart(rec.id, "m", 10_001, ByteArray(1)) }.isInstanceOf(DomainError.ValidationFailed::class.java)
        assertThatThrownBy { service.uploadPart(rec.id, "m", 1, ByteArray(0)) }.isInstanceOf(DomainError.ValidationFailed::class.java)
        assertThatThrownBy {
            service.uploadPart(
                rec.id,
                "m",
                1,
                ByteArray(65 * 1024 * 1024),
            )
        }.isInstanceOf(DomainError.PayloadTooLarge::class.java)
        assertThatThrownBy { service.uploadPart(UUID.randomUUID(), "m", 1, ByteArray(1)) }.isInstanceOf(DomainError.NotFound::class.java)
    }

    @Test
    fun `complete multipart assembles the parts, checks size and sniffed type, then stores the real values`() {
        val rec = record(size = 1000)
        every { repository.findById(rec.id) } returns rec
        every { storage.listParts(any(), "mp-1") } returns listOf(PartEtag(2, "b"), PartEtag(1, "a"))
        every { storage.size(any()) } returns 1000
        every { storage.read(any(), 0, 1000) } returns ByteArray(1000)
        every { sniffer.detect(any()) } returns "video/mp4"
        every { repository.updateStored(rec.id, "video/mp4", 1000) } returns rec

        val stored = service.completeMultipart(rec.id, "mp-1", 1000)

        assertThat(stored.sizeBytes).isEqualTo(1000)
        verify { storage.completeMultipart(any(), "mp-1", listOf(PartEtag(2, "b"), PartEtag(1, "a"))) }
        assertThat(meters.counter("drift.files.uploaded", "kind", "media").count()).isEqualTo(1.0)
    }

    @Test
    fun `complete multipart discards the file when the size does not match`() {
        val rec = record(size = 1000)
        every { repository.findById(rec.id) } returns rec
        every { storage.listParts(any(), any()) } returns listOf(PartEtag(1, "a"))
        every { storage.size(any()) } returns 900
        every { repository.deleteById(rec.id) } just runs

        assertThatThrownBy { service.completeMultipart(rec.id, "mp-1", 1000) }.isInstanceOf(DomainError.ValidationFailed::class.java)
        verify { storage.delete(any()) }
        verify { repository.deleteById(rec.id) }
    }

    @Test
    fun `complete multipart discards the file when the content is not allowed`() {
        val rec = record(size = 10)
        every { repository.findById(rec.id) } returns rec
        every { storage.listParts(any(), any()) } returns listOf(PartEtag(1, "a"))
        every { storage.size(any()) } returns 10
        every { storage.read(any(), any(), any()) } returns ByteArray(10)
        every { sniffer.detect(any()) } returns "application/x-msdownload"
        every { repository.deleteById(rec.id) } just runs

        assertThatThrownBy { service.completeMultipart(rec.id, "mp-1", 10) }.isInstanceOf(DomainError.UnsupportedMediaType::class.java)
        verify { repository.deleteById(rec.id) }
    }

    @Test
    fun `complete multipart with no parts is rejected`() {
        val rec = record()
        every { repository.findById(rec.id) } returns rec
        every { storage.listParts(any(), any()) } returns emptyList()

        assertThatThrownBy { service.completeMultipart(rec.id, "mp-1", 10) }.isInstanceOf(DomainError.ValidationFailed::class.java)
    }

    @Test
    fun `abort removes the multipart upload and the record, and is quiet for unknown files`() {
        val rec = record()
        every { repository.findById(rec.id) } returns rec
        every { repository.findById(neq(rec.id)) } returns null
        every { repository.deleteById(rec.id) } just runs

        service.abortMultipart(rec.id, "mp-1")
        service.abortMultipart(UUID.randomUUID(), "mp-2")

        verify(exactly = 1) { storage.abortMultipart(any(), "mp-1") }
        verify { repository.deleteById(rec.id) }
    }

    @Test
    fun `abort still removes the record when MinIO has already forgotten the upload`() {
        val rec = record()
        every { repository.findById(rec.id) } returns rec
        every { storage.abortMultipart(any(), any()) } throws IllegalStateException("NoSuchUpload")
        every { repository.deleteById(rec.id) } just runs

        service.abortMultipart(rec.id, "mp-1")

        verify { repository.deleteById(rec.id) }
    }

    // ── delete / metadata ───────────────────────────────────────────────────

    @Test
    fun `delete removes object and record, and reports whether the file existed`() {
        val rec = record()
        every { repository.findById(rec.id) } returns rec
        every { repository.findById(neq(rec.id)) } returns null
        every { repository.deleteById(rec.id) } just runs

        assertThat(service.delete(rec.id)).isTrue()
        assertThat(service.delete(UUID.randomUUID())).isFalse()
        verify { storage.delete(any()) }
    }

    @Test
    fun `delete keeps going when the object is already gone`() {
        val rec = record()
        every { repository.findById(rec.id) } returns rec
        every { storage.delete(any()) } throws IllegalStateException("gone")
        every { repository.deleteById(rec.id) } just runs

        assertThat(service.delete(rec.id)).isTrue()
        verify { repository.deleteById(rec.id) }
    }

    @Test
    fun `metadata returns the record or null`() {
        val rec = record()
        every { repository.findById(rec.id) } returns rec
        every { repository.findById(neq(rec.id)) } returns null

        assertThat(service.metadata(rec.id)).isEqualTo(rec)
        assertThat(service.metadata(UUID.randomUUID())).isNull()
    }

    // ── open (REST download) ────────────────────────────────────────────────

    @Test
    fun `members can open room media and get the requested range`() {
        val rec = record(size = 1000)
        val user = UUID.randomUUID()
        every { repository.findById(rec.id) } returns rec
        every { rooms.isMember(room, user) } returns true
        every { storage.size(any()) } returns 1000
        val opened = slot<Long>()
        every { storage.open(any(), capture(opened), any()) } returns "x".byteInputStream()

        val content = service.open(rec.id, user, "bytes=100-199")

        assertThat(content.partial).isTrue()
        assertThat(content.range.start).isEqualTo(100)
        assertThat(content.range.length).isEqualTo(100)
        content.open().close()
        assertThat(opened.captured).isEqualTo(100)
    }

    @Test
    fun `no range header means the whole file with a full response`() {
        val rec = record(size = 1000)
        every { repository.findById(rec.id) } returns rec
        every { rooms.isMember(any(), any()) } returns true
        every { storage.size(any()) } returns 1000

        val content = service.open(rec.id, UUID.randomUUID(), null)

        assertThat(content.partial).isFalse()
        assertThat(content.range.length).isEqualTo(1000)
    }

    @Test
    fun `non-members are forbidden and deleted rooms look like missing files`() {
        val rec = record()
        every { repository.findById(rec.id) } returns rec
        every { rooms.isMember(room, any()) } returns false
        assertThatThrownBy { service.open(rec.id, UUID.randomUUID(), null) }.isInstanceOf(DomainError.Forbidden::class.java)

        every { rooms.isMember(room, any()) } returns null
        assertThatThrownBy { service.open(rec.id, UUID.randomUUID(), null) }.isInstanceOf(DomainError.NotFound::class.java)
    }

    @Test
    fun `avatars only need a valid session, not room membership`() {
        val rec = record(kind = FileKind.AVATAR, roomId = null, size = 10)
        every { repository.findById(rec.id) } returns rec
        every { storage.size(any()) } returns 10

        service.open(rec.id, UUID.randomUUID(), null)

        verify(exactly = 0) { rooms.isMember(any(), any()) }
    }

    @Test
    fun `missing records and missing objects are not found`() {
        every { repository.findById(any()) } returns null
        assertThatThrownBy { service.open(UUID.randomUUID(), UUID.randomUUID(), null) }.isInstanceOf(DomainError.NotFound::class.java)

        val rec = record(kind = FileKind.AVATAR, roomId = null)
        every { repository.findById(rec.id) } returns rec
        every { storage.size(any()) } returns null
        assertThatThrownBy { service.open(rec.id, UUID.randomUUID(), null) }.isInstanceOf(DomainError.NotFound::class.java)
    }

    @Test
    fun `media without a room id cannot be opened`() {
        val rec = record(roomId = null)
        every { repository.findById(rec.id) } returns rec
        assertThatThrownBy { service.open(rec.id, UUID.randomUUID(), null) }.isInstanceOf(DomainError.NotFound::class.java)
    }

    // ── abandoned uploads ───────────────────────────────────────────────────

    @Test
    fun `the sweep removes old records whose object never arrived and keeps finished ones`() {
        val a = record().copy(objectKey = "media/x/abandoned")
        val f = record().copy(objectKey = "media/x/finished")
        every { repository.findMediaOlderThan(any()) } returns listOf(a, f)
        every { storage.size(match { it.key == "media/x/abandoned" }) } returns null
        every { storage.size(match { it.key == "media/x/finished" }) } returns 1000
        every { repository.deleteById(a.id) } just runs

        assertThat(service.sweepAbandonedUploads(java.time.Duration.ofDays(2))).isEqualTo(1)

        verify { repository.deleteById(a.id) }
        verify(exactly = 0) { repository.deleteById(f.id) }
    }

    @Test
    fun `the sweep only looks at records older than the given age`() {
        val threshold = slot<Instant>()
        every { repository.findMediaOlderThan(capture(threshold)) } returns emptyList()

        service.sweepAbandonedUploads(java.time.Duration.ofDays(2))

        assertThat(threshold.captured).isEqualTo(now.minus(java.time.Duration.ofDays(2)))
    }

    // ── cascades ────────────────────────────────────────────────────────────

    @Test
    fun `deleting a room removes all its files`() {
        val a = record()
        val b = record()
        every { repository.findByRoom(room) } returns listOf(a, b)
        every { repository.deleteById(any()) } just runs

        assertThat(service.deleteByRoom(room)).isEqualTo(2)
        verify(exactly = 2) { storage.delete(any()) }
    }

    @Test
    fun `deleting a user removes their avatars`() {
        val avatar = record(kind = FileKind.AVATAR, roomId = null)
        every { repository.findByOwnerAndKind(owner, FileKind.AVATAR) } returns listOf(avatar)
        every { repository.deleteById(avatar.id) } just runs

        assertThat(service.deleteAvatarsOwnedBy(owner)).isEqualTo(1)
    }
}
