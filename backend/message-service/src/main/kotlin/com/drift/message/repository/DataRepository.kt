package com.drift.message.repository

import com.drift.message.models.MessageEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface DataRepository : JpaRepository<MessageEntity, UUID> {
    @Query("select m from MessageEntity m where m.roomId = :roomId order by m.createdAt desc, m.id desc")
    fun newest(
        @Param("roomId") roomId: UUID,
        pageable: Pageable,
    ): List<MessageEntity>

    @Query(
        "select m from MessageEntity m where m.roomId = :roomId and (m.createdAt < :at or (m.createdAt = :at and m.id < :id)) " +
            "order by m.createdAt desc, m.id desc",
    )
    fun olderThan(
        @Param("roomId") roomId: UUID,
        @Param("at") at: Instant,
        @Param("id") id: UUID,
        pageable: Pageable,
    ): List<MessageEntity>

    @Modifying
    @Query("delete from MessageEntity m where m.roomId = :roomId")
    fun deleteAllInRoom(
        @Param("roomId") roomId: UUID,
    ): Int

    @Modifying
    @Query("update MessageEntity m set m.senderDeleted = true, m.senderId = null where m.senderId = :userId")
    fun markDeleted(
        @Param("userId") userId: UUID,
    ): Int
}
