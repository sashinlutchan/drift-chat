package com.drift.message.models

import java.time.Instant
import java.util.UUID

/** A message exactly as stored: content is encrypted (spec sections 18 and 19). */
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

/** A decrypted message ready to be shown. */
data class Message(
    val id: UUID,
    val roomId: UUID,
    val senderId: UUID?,
    val senderDeleted: Boolean,
    val type: MessageType,
    /** Message text, or the file name for media messages. */
    val text: String,
    val fileId: UUID?,
    val createdAt: Instant,
)

/** A page of history, newest first, with the cursor for the next (older) page. */
data class MessagePage(
    val messages: List<Message>,
    val nextCursor: UUID?,
)

/** Public profile fields needed to show who sent a message. */
data class UserProfile(
    val id: UUID,
    val username: String,
    val name: String,
    val avatarFileId: UUID?,
    val online: Boolean,
)

/** Who is connected on a socket. */
data class SocketUser(
    val userId: UUID,
    val sessionId: String,
)

/** Result of asking room-service about a room. */
enum class RoomAccess {
    MEMBER,
    NOT_MEMBER,
    NO_SUCH_ROOM,
}

/** A multipart upload started with file-service. */
data class StartedUpload(
    val fileId: UUID,
    val multipartId: String,
)

/** A finished upload as reported by file-service (type sniffed from the bytes). */
data class CompletedUpload(
    val fileId: UUID,
    val mimeType: String,
    val sizeBytes: Long,
)

/** An upload in progress; lives in Valkey for `UPLOAD_SESSION_TTL` (spec section 13.6). */
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
