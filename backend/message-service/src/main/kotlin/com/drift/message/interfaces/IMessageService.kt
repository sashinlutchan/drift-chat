package com.drift.message.interfaces

import com.drift.message.models.Message
import com.drift.message.models.MessagePage
import com.drift.message.models.MessageType
import com.drift.message.models.StoredMessage
import java.util.UUID

interface IMessageService {
    fun sendText(
        roomId: UUID,
        senderId: UUID,
        text: String,
        clientId: String,
    ): Message

    fun sendMedia(
        roomId: UUID,
        senderId: UUID,
        fileId: UUID,
        fileName: String,
        type: MessageType,
    ): Message

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

    fun decrypt(stored: StoredMessage): Message

    fun deleteRoom(roomId: UUID): Int

    fun markSenderDeleted(userId: UUID): Int
}
