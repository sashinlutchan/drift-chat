package com.drift.message.repository

import com.drift.message.models.StoredMessage
import java.util.UUID

/** Repository port for messages (spec section 21 layering rule): services never see Spring Data. */
interface IMessageRepository {
    fun save(message: StoredMessage): StoredMessage

    fun findById(id: UUID): StoredMessage?

    /** Newest first; [before] is the id of the oldest message already loaded. */
    fun page(
        roomId: UUID,
        before: UUID?,
        limit: Int,
    ): List<StoredMessage>

    fun deleteByRoom(roomId: UUID): Int

    /** Marks a user's messages as sent by a deleted user and clears the sender id. */
    fun markSenderDeleted(userId: UUID): Int
}
