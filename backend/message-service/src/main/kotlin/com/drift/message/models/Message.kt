package com.drift.message.models

import java.time.Instant
import java.util.UUID

data class StoredMessage(
    val id: UUID,
    val roomId: UUID,
    val senderId: UUID?,
    val senderDeleted: Boolean,
    val type: MessageType,
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val keyVersion: Int,
    val fileId: UUID?,
    val createdAt: Instant,
) {
    override fun equals(other: Any?): Boolean = other is StoredMessage && other.id == id

    override fun hashCode(): Int = id.hashCode()
}

data class Message(
    val id: UUID,
    val roomId: UUID,
    val senderId: UUID?,
    val senderDeleted: Boolean,
    val type: MessageType,
    val text: String,
    val fileId: UUID?,
    val createdAt: Instant,
)

data class MessagePage(
    val messages: List<Message>,
    val nextCursor: UUID?,
)

data class UserProfile(
    val id: UUID,
    val username: String,
    val name: String,
    val avatarFileId: UUID?,
    val online: Boolean,
)

data class SocketUser(
    val userId: UUID,
    val sessionId: String,
)

enum class RoomAccess {
    MEMBER,
    NOT_MEMBER,
    NO_SUCH_ROOM,
}

data class StartedUpload(
    val fileId: UUID,
    val multipartId: String,
)

data class CompletedUpload(
    val fileId: UUID,
    val mimeType: String,
    val sizeBytes: Long,
)

data class UploadSession(
    val id: String,
    val roomId: UUID,
    val uploaderId: UUID,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val fileId: UUID,
    val multipartId: String,
    val chunkSize: Long,
    val totalChunks: Int,
)
