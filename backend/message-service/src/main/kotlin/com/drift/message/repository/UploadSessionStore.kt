package com.drift.message.repository

import com.drift.message.interfaces.IUploadSessionStore
import com.drift.message.models.UploadSession
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
class UploadSessionStore(
    private val redis: StringRedisTemplate,
) : IUploadSessionStore {
    override fun create(
        session: UploadSession,
        ttl: Duration,
    ) {
        redis.opsForHash<String, String>().putAll(
            key(session.id),
            mapOf(
                "roomId" to session.roomId.toString(),
                "uploaderId" to session.uploaderId.toString(),
                "fileName" to session.fileName,
                "sizeBytes" to session.sizeBytes.toString(),
                "mimeType" to session.mimeType,
                "fileId" to session.fileId.toString(),
                "multipartId" to session.multipartId,
                "chunkSize" to session.chunkSize.toString(),
                "totalChunks" to session.totalChunks.toString(),
            ),
        )
        redis.expire(key(session.id), ttl)
    }

    override fun find(id: String): UploadSession? {
        val h = redis.opsForHash<String, String>().entries(key(id))
        if (h.isEmpty()) return null
        return UploadSession(
            id = id,
            roomId = UUID.fromString(h.getValue("roomId")),
            uploaderId = UUID.fromString(h.getValue("uploaderId")),
            fileName = h.getValue("fileName"),
            sizeBytes = h.getValue("sizeBytes").toLong(),
            mimeType = h.getValue("mimeType"),
            fileId = UUID.fromString(h.getValue("fileId")),
            multipartId = h.getValue("multipartId"),
            chunkSize = h.getValue("chunkSize").toLong(),
            totalChunks = h.getValue("totalChunks").toInt(),
        )
    }

    override fun markChunk(
        id: String,
        index: Int,
        ttl: Duration,
    ) {
        redis.opsForSet().add(chunks(id), index.toString())
        redis.expire(chunks(id), ttl)
        redis.expire(key(id), ttl)
    }

    override fun received(id: String): Set<Int> =
        redis
            .opsForSet()
            .members(chunks(id))
            .orEmpty()
            .map(String::toInt)
            .toSortedSet()

    override fun delete(id: String) {
        redis.delete(listOf(key(id), chunks(id)))
    }

    private fun key(id: String) = "upload:$id"

    private fun chunks(id: String) = "upload:$id:chunks"
}
