package com.drift.message.interfaces

import com.drift.message.models.Message
import com.drift.message.models.MessagePage
import com.drift.message.models.MessageType
import com.drift.message.models.StoredMessage
import java.util.UUID

/** Message business logic (spec sections 7 and 13.3); implemented by `service/MessageService`. */
interface IMessageService {
    /**
     * Validates, encrypts, saves and publishes a text message. A retry with the same [clientId] returns the first
     * message instead of creating another.
     */
    fun sendText(
        roomId: UUID,
        senderId: UUID,
        text: String,
        clientId: String,
    ): Message

    /** A media message for a finished upload. */
    fun sendMedia(
        roomId: UUID,
        senderId: UUID,
        fileId: UUID,
        fileName: String,
        type: MessageType,
    ): Message

    /** An inline notice such as "Sipho joined the room". */
    fun sendSystem(
        roomId: UUID,
        subjectId: UUID?,
        text: String,
    ): Message

    fun history(
        userId: UUID,
        roomId: UUID,
        before: UUID?,
        limit: Int?,
    ): MessagePage

    /** Decrypts a message that arrived over Kafka (still encrypted). */
    fun decrypt(stored: StoredMessage): Message

    fun deleteRoom(roomId: UUID): Int

    fun markSenderDeleted(userId: UUID): Int
}
