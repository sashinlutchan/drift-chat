package com.drift.message.service

import com.drift.message.config.AppProperties
import com.drift.message.exceptions.DomainError
import com.drift.message.interfaces.IFileStore
import com.drift.message.interfaces.IMessageService
import com.drift.message.interfaces.IRoomMembership
import com.drift.message.interfaces.IUploadService
import com.drift.message.interfaces.IUploadSessionStore
import com.drift.message.models.Message
import com.drift.message.models.MessageType
import com.drift.message.models.RoomAccess
import com.drift.message.models.StartedUpload
import com.drift.message.models.UploadSession
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import java.io.InputStream
import java.util.UUID

@Service
class UploadService(
    private val files: IFileStore,
    private val sessions: IUploadSessionStore,
    private val messages: IMessageService,
    private val rooms: IRoomMembership,
    private val properties: AppProperties,
    private val meters: MeterRegistry,
) : IUploadService {
    private val allowed = properties.allowedMimeTypes.map { it.trim().lowercase() }.toSet()

    override fun start(
        userId: UUID,
        roomId: UUID,
        fileName: String,
        sizeBytes: Long,
        mimeType: String,
    ): UploadSession {
        when (rooms.access(roomId, userId)) {
            RoomAccess.NO_SUCH_ROOM -> throw DomainError.NotFound("This room no longer exists.")
            RoomAccess.NOT_MEMBER -> throw DomainError.Forbidden("Join this room to send files.")
            RoomAccess.MEMBER -> Unit
        }
        val name = fileName.trim().substringAfterLast('/').substringAfterLast('\\')
        if (name.isEmpty()) throw DomainError.ValidationFailed("File name is required.")
        if (sizeBytes <= 0) throw DomainError.ValidationFailed("The file is empty.")
        if (sizeBytes > properties.maxFileSize.toBytes()) {
            throw DomainError.PayloadTooLarge("Files can be at most ${humanSize(properties.maxFileSize.toBytes())}.")
        }
        val mime = mimeType.trim().lowercase()
        if (mime !in allowed ||
            CAN_EXECUTE.any(mime::contains)
        ) {
            throw DomainError.UnsupportedMediaType("Files of type $mime are not supported.")
        }

        val chunkSize = properties.uploadChunkSize.toBytes()
        val started = files.start(userId, roomId, name, mime, sizeBytes)
        val session =
            UploadSession(
                id = UUID.randomUUID().toString(),
                roomId = roomId,
                uploaderId = userId,
                fileName = name,
                sizeBytes = sizeBytes,
                mimeType = mime,
                fileId = started.fileId,
                multipartId = started.multipartId,
                chunkSize = chunkSize,
                totalChunks = ((sizeBytes + chunkSize - 1) / chunkSize).toInt(),
            )
        sessions.create(session, properties.uploadSessionTtl)
        meters.counter("drift.uploads.started").increment()
        return session
    }

    override fun uploadChunk(
        userId: UUID,
        uploadId: String,
        index: Int,
        length: Long,
        content: InputStream,
    ) {
        val session = owned(userId, uploadId)
        if (index !in 0 until session.totalChunks) {
            throw DomainError.ValidationFailed("Chunk index must be between 0 and ${session.totalChunks - 1}.")
        }
        val expected = if (index == session.totalChunks - 1) session.sizeBytes - index * session.chunkSize else session.chunkSize
        if (length != expected) throw DomainError.ValidationFailed("Chunk $index must be exactly $expected bytes but was $length.")

        files.uploadPart(StartedUpload(session.fileId, session.multipartId), index + 1, content, length)
        sessions.markChunk(uploadId, index, properties.uploadSessionTtl)
    }

    override fun status(
        userId: UUID,
        uploadId: String,
    ): Pair<UploadSession, Set<Int>> = owned(userId, uploadId).let { it to sessions.received(uploadId) }

    override fun complete(
        userId: UUID,
        uploadId: String,
    ): Message {
        val session = owned(userId, uploadId)
        val missing = (0 until session.totalChunks).toSet() - sessions.received(uploadId)
        if (missing.isNotEmpty()) throw DomainError.Conflict("Missing chunks: ${missing.sorted()}")

        val finished =
            try {
                files.complete(StartedUpload(session.fileId, session.multipartId), session.sizeBytes)
            } catch (e: DomainError) {
                sessions.delete(uploadId)
                throw e
            }
        val type =
            MessageType.forMime(finished.mimeType)
                ?: throw DomainError.UnsupportedMediaType("Files of type ${finished.mimeType} are not supported.")
        val message = messages.sendMedia(session.roomId, userId, finished.fileId, session.fileName, type)
        sessions.delete(uploadId)
        meters.counter("drift.uploads.completed", "type", type.dbValue).increment()
        meters.summary("drift.uploads.size", "type", type.dbValue).record(finished.sizeBytes.toDouble())
        return message
    }

    override fun cancel(
        userId: UUID,
        uploadId: String,
    ) {
        val session = sessions.find(uploadId) ?: return
        if (session.uploaderId != userId) throw DomainError.Forbidden("This isn't your upload.")
        files.abort(StartedUpload(session.fileId, session.multipartId))
        sessions.delete(uploadId)
    }

    private fun owned(
        userId: UUID,
        uploadId: String,
    ): UploadSession {
        val session = sessions.find(uploadId) ?: throw DomainError.NotFound("This upload doesn't exist or has expired.")
        if (session.uploaderId != userId) throw DomainError.Forbidden("This isn't your upload.")
        return session
    }

    private companion object {
        val CAN_EXECUTE = listOf("svg", "xml", "html", "javascript", "ecmascript", "script", "executable", "x-sh")

        const val MIB = 1024L * 1024
        const val GIB = 1024 * MIB

        fun humanSize(bytes: Long): String = if (bytes % GIB == 0L) "${bytes / GIB} GB" else "${bytes / MIB} MB"
    }
}
