package com.drift.message.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "messages")
class MessageEntity(
    @Id
    @Column(name = "id")
    var id: UUID,
    @Column(name = "room_id", nullable = false)
    var roomId: UUID,
    @Column(name = "sender_id")
    var senderId: UUID?,
    @Column(name = "sender_deleted", nullable = false)
    var senderDeleted: Boolean,
    @Column(name = "type", nullable = false)
    var type: String,
    @Column(name = "ciphertext", nullable = false)
    var ciphertext: ByteArray,
    @Column(name = "nonce", nullable = false)
    var nonce: ByteArray,
    @Column(name = "key_version", nullable = false)
    var keyVersion: Int,
    @Column(name = "file_id")
    var fileId: UUID?,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
) {
    fun toStored(): StoredMessage =
        StoredMessage(id, roomId, senderId, senderDeleted, MessageType.fromDb(type), ciphertext, nonce, keyVersion, fileId, createdAt)

    override fun equals(other: Any?): Boolean = other is MessageEntity && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "MessageEntity(id=$id, roomId=$roomId, type=$type)"

    companion object {
        fun from(message: StoredMessage): MessageEntity =
            MessageEntity(
                message.id,
                message.roomId,
                message.senderId,
                message.senderDeleted,
                message.type.dbValue,
                message.ciphertext,
                message.nonce,
                message.keyVersion,
                message.fileId,
                message.createdAt,
            )
    }
}
