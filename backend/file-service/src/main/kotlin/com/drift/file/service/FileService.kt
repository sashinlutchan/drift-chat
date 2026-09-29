package com.drift.file.service

import com.drift.file.config.AppProperties
import com.drift.file.exceptions.DomainError
import com.drift.file.interfaces.IFileService
import com.drift.file.interfaces.IMimeSniffer
import com.drift.file.interfaces.IObjectStorage
import com.drift.file.interfaces.IRoomMembership
import com.drift.file.models.ByteRange
import com.drift.file.models.FileContent
import com.drift.file.models.FileKind
import com.drift.file.models.FileRecord
import com.drift.file.models.MultipartStart
import com.drift.file.models.NewFile
import com.drift.file.models.ObjectLocation
import com.drift.file.models.PartEtag
import com.drift.file.models.StoredFile
import com.drift.file.models.UploadInfo
import com.drift.file.repository.IFileRepository
import com.drift.file.utils.MimeSniffer
import com.drift.file.utils.RangeParser
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * File business logic (spec sections 13.4 and 13.6). Talks only to the [IFileRepository], [IObjectStorage],
 * [IMimeSniffer] and [IRoomMembership] ports. Files are never trusted: the type is sniffed from the bytes and
 * the size is checked against what actually reached storage.
 */
@Service
class FileService(
    private val repository: IFileRepository,
    private val storage: IObjectStorage,
    private val sniffer: IMimeSniffer,
    private val rooms: IRoomMembership,
    private val properties: AppProperties,
    private val meters: MeterRegistry,
    private val clock: java.time.Clock,
) : IFileService {
    private val allowed: Set<String> =
        properties.storage.allowedMimeTypes
            .map { MimeSniffer.normalize(it.trim()) }
            .toSet()

    override fun upload(
        info: UploadInfo,
        content: ByteArray,
    ): StoredFile {
        val limit = if (info.kind == FileKind.AVATAR) properties.maxAvatarSize.toBytes() else properties.storage.maxFileSize.toBytes()
        if (content.isEmpty()) throw DomainError.ValidationFailed("The file is empty.")
        if (content.size > limit) throw DomainError.PayloadTooLarge("The file is larger than the allowed $limit bytes.")
        val mime = sniffer.detect(content.copyOf(minOf(content.size, HEAD_BYTES)))
        requireAllowed(mime, info.kind)

        val id = UUID.randomUUID()
        val location = locationFor(info.kind, info.roomId, id)
        storage.put(location, content, mime)
        try {
            repository.save(NewFile(id, info.ownerId, info.roomId, info.kind, mime, content.size.toLong(), location.key))
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            runCatching { storage.delete(location) }
            throw e
        }
        recordUpload(info.kind, content.size.toLong())
        return StoredFile(id, mime, content.size.toLong())
    }

    override fun startMultipart(
        info: UploadInfo,
        sizeBytes: Long,
    ): MultipartStart {
        if (info.kind != FileKind.MEDIA || info.roomId == null) {
            throw DomainError.ValidationFailed("Multipart uploads are for room media and need a room id.")
        }
        if (sizeBytes <= 0) throw DomainError.ValidationFailed("The file is empty.")
        if (sizeBytes > properties.storage.maxFileSize.toBytes()) {
            throw DomainError.PayloadTooLarge("Files can be at most ${properties.storage.maxFileSize.toMegabytes()} MB.")
        }
        val declared = MimeSniffer.normalize(info.declaredMimeType)
        if (declared !in allowed) throw DomainError.UnsupportedMediaType("Files of type $declared are not supported.")

        val id = UUID.randomUUID()
        val location = locationFor(info.kind, info.roomId, id)
        val multipartId = storage.startMultipart(location, declared)
        repository.save(NewFile(id, info.ownerId, info.roomId, info.kind, declared, sizeBytes, location.key))
        return MultipartStart(id, multipartId)
    }

    override fun uploadPart(
        fileId: UUID,
        multipartId: String,
        partNumber: Int,
        data: ByteArray,
    ): PartEtag {
        if (partNumber !in 1..MAX_PART_NUMBER) throw DomainError.ValidationFailed("Part number must be between 1 and $MAX_PART_NUMBER.")
        if (data.isEmpty()) throw DomainError.ValidationFailed("The part is empty.")
        if (data.size > MAX_PART_BYTES) throw DomainError.PayloadTooLarge("A part can be at most $MAX_PART_BYTES bytes.")
        val record = requireRecord(fileId)
        val etag = storage.uploadPart(locationOf(record), multipartId, partNumber, data)
        return PartEtag(partNumber, etag)
    }

    override fun completeMultipart(
        fileId: UUID,
        multipartId: String,
        expectedSizeBytes: Long,
    ): StoredFile {
        val record = requireRecord(fileId)
        val location = locationOf(record)
        val parts = storage.listParts(location, multipartId)
        if (parts.isEmpty()) throw DomainError.ValidationFailed("No parts have been uploaded.")
        storage.completeMultipart(location, multipartId, parts)

        val size = storage.size(location) ?: throw DomainError.NotFound("The uploaded file could not be found.")
        val expected = if (expectedSizeBytes > 0) expectedSizeBytes else record.sizeBytes
        val mime =
            try {
                check(size == expected) { "Uploaded $size bytes but expected $expected." }
                check(size <= properties.storage.maxFileSize.toBytes()) { "The file is larger than allowed." }
                val mime = sniffer.detect(storage.read(location, 0, minOf(size, HEAD_BYTES.toLong())))
                requireAllowed(mime, record.kind)
                mime
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                discard(record)
                throw if (e is DomainError) e else DomainError.ValidationFailed(e.message ?: "The upload is invalid.")
            }
        repository.updateStored(fileId, mime, size)
        recordUpload(record.kind, size)
        return StoredFile(fileId, mime, size)
    }

    override fun abortMultipart(
        fileId: UUID,
        multipartId: String,
    ) {
        val record = repository.findById(fileId) ?: return
        runCatching { storage.abortMultipart(locationOf(record), multipartId) }
            .onFailure { log.warn { "Abort of $fileId: ${it.message}" } }
        repository.deleteById(fileId)
    }

    override fun delete(fileId: UUID): Boolean {
        val record = repository.findById(fileId) ?: return false
        discard(record)
        return true
    }

    override fun metadata(fileId: UUID): FileRecord? = repository.findById(fileId)

    override fun open(
        fileId: UUID,
        userId: UUID,
        rangeHeader: String?,
    ): FileContent {
        val record = requireRecord(fileId)
        if (record.kind == FileKind.MEDIA) {
            val roomId = record.roomId ?: throw DomainError.NotFound("File not found.")
            when (rooms.isMember(roomId, userId)) {
                null -> throw DomainError.NotFound("File not found.")
                false -> throw DomainError.Forbidden("You are not a member of this room.")
                true -> Unit
            }
        }
        val location = locationOf(record)
        val size = storage.size(location) ?: throw DomainError.NotFound("File not found.")
        val range = RangeParser.parse(rangeHeader, size)
        val effective = range ?: ByteRange(0, size - 1)
        return FileContent(record.copy(sizeBytes = size), effective, range != null) {
            storage.open(location, effective.start, effective.length)
        }
    }

    override fun sweepAbandonedUploads(olderThan: java.time.Duration): Int =
        repository
            .findMediaOlderThan(clock.instant().minus(olderThan))
            .filter { storage.size(locationOf(it)) == null }
            .onEach { repository.deleteById(it.id) }
            .size

    override fun deleteByRoom(roomId: UUID): Int = repository.findByRoom(roomId).onEach(::discard).size

    override fun deleteAvatarsOwnedBy(userId: UUID): Int = repository.findByOwnerAndKind(userId, FileKind.AVATAR).onEach(::discard).size

    private fun requireRecord(id: UUID): FileRecord = repository.findById(id) ?: throw DomainError.NotFound("File not found.")

    private fun requireAllowed(
        mime: String,
        kind: FileKind,
    ) {
        val kindOk = if (kind == FileKind.AVATAR) mime.startsWith("image/") else MEDIA_PREFIXES.any(mime::startsWith)
        if (!kindOk || mime !in allowed) throw DomainError.UnsupportedMediaType("Files of type $mime are not supported.")
    }

    private fun discard(record: FileRecord) {
        runCatching {
            storage.delete(
                locationOf(record),
            )
        }.onFailure { log.warn { "Could not delete object ${record.objectKey}: ${it.message}" } }
        repository.deleteById(record.id)
    }

    private fun recordUpload(
        kind: FileKind,
        size: Long,
    ) {
        meters.counter("drift.files.uploaded", "kind", kind.name.lowercase()).increment()
        meters.summary("drift.files.upload.size", "kind", kind.name.lowercase()).record(size.toDouble())
    }

    private fun bucketFor(kind: FileKind) =
        if (kind ==
            FileKind.AVATAR
        ) {
            properties.storage.minioBucketAvatars
        } else {
            properties.storage.minioBucketMedia
        }

    private fun locationFor(
        kind: FileKind,
        roomId: UUID?,
        id: UUID,
    ) = ObjectLocation(bucketFor(kind), "${kind.name.lowercase()}/${roomId ?: "none"}/$id")

    private fun locationOf(record: FileRecord) = ObjectLocation(bucketFor(record.kind), record.objectKey)

    private companion object {
        const val HEAD_BYTES = 64 * 1024
        const val MAX_PART_NUMBER = 10_000
        const val MAX_PART_BYTES = 64 * 1024 * 1024
        val MEDIA_PREFIXES = listOf("image/", "audio/", "video/")
    }
}
