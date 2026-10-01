package com.drift.message.repository

import com.drift.message.models.StoredMessage
import java.util.UUID

interface IMessageRepository {
    fun save(message: StoredMessage): StoredMessage

    fun findById(id: UUID): StoredMessage?

    fun page(
        roomId: UUID,
        before: UUID?,
        limit: Int,
    ): List<StoredMessage>

    fun deleteByRoom(roomId: UUID): Int

    fun markSenderDeleted(userId: UUID): Int
}
