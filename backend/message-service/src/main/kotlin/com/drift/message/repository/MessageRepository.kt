package com.drift.message.repository

import com.drift.message.models.MessageEntity
import com.drift.message.models.StoredMessage
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
class MessageRepository(
    private val data: DataRepository,
) : IMessageRepository {
    @Transactional
    override fun save(message: StoredMessage): StoredMessage = data.save(MessageEntity.from(message)).toStored()

    override fun findById(id: UUID): StoredMessage? = data.findById(id).map(MessageEntity::toStored).orElse(null)

    override fun page(
        roomId: UUID,
        before: UUID?,
        limit: Int,
    ): List<StoredMessage> {
        val pageable = PageRequest.of(0, limit)
        val cursor = before?.let { data.findById(it).orElse(null) }
        val rows =
            if (cursor == null || cursor.roomId != roomId) {
                data.newest(roomId, pageable)
            } else {
                data.olderThan(roomId, cursor.createdAt, cursor.id, pageable)
            }
        return rows.map(MessageEntity::toStored)
    }

    @Transactional
    override fun deleteByRoom(roomId: UUID): Int = data.deleteAllInRoom(roomId)

    @Transactional
    override fun markSenderDeleted(userId: UUID): Int = data.markDeleted(userId)
}
