package com.drift.room.repository

import com.drift.room.models.RoomEntity
import com.drift.room.models.RoomMemberEntity
import com.drift.room.models.RoomMemberId
import com.drift.room.models.RoomPinEntity
import com.drift.room.models.RoomPinId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface DataRepository : JpaRepository<RoomEntity, UUID> {
    fun findBySlug(slug: String): RoomEntity?

    fun existsBySlug(slug: String): Boolean

    @Query("select r from RoomEntity r where lower(r.name) like lower(concat('%', :search, '%'))")
    fun search(
        @Param("search") search: String,
    ): List<RoomEntity>

    fun findByEmptySinceBefore(threshold: Instant): List<RoomEntity>

    @Modifying
    @Query("update RoomEntity r set r.createdBy = null where r.createdBy = :userId")
    fun clearAuthor(
        @Param("userId") userId: UUID,
    ): Int

    @Modifying
    @Query("update RoomEntity r set r.emptySince = null, r.lastActiveAt = :now where r.id = :roomId")
    fun occupied(
        @Param("roomId") roomId: UUID,
        @Param("now") now: Instant,
    ): Int

    @Modifying
    @Query("update RoomEntity r set r.emptySince = :now where r.id = :roomId and r.emptySince is null")
    fun empty(
        @Param("roomId") roomId: UUID,
        @Param("now") now: Instant,
    ): Int
}

interface MembersDataRepository : JpaRepository<RoomMemberEntity, RoomMemberId> {
    @Query("select m from RoomMemberEntity m where m.id.roomId in :roomIds and m.leftAt is null")
    fun activeIn(
        @Param("roomIds") roomIds: Collection<UUID>,
    ): List<RoomMemberEntity>

    @Query("select m from RoomMemberEntity m where m.id.userId = :userId and m.leftAt is not null")
    fun leftOf(
        @Param("userId") userId: UUID,
    ): List<RoomMemberEntity>

    @Query("select m from RoomMemberEntity m where m.id.userId = :userId and m.leftAt is null")
    fun activeOf(
        @Param("userId") userId: UUID,
    ): List<RoomMemberEntity>

    @Modifying
    @Query("delete from RoomMemberEntity m where m.id.userId = :userId")
    fun deleteAllOf(
        @Param("userId") userId: UUID,
    ): Int
}

interface PinsDataRepository : JpaRepository<RoomPinEntity, RoomPinId> {
    @Query("select p from RoomPinEntity p where p.id.userId = :userId")
    fun ofUser(
        @Param("userId") userId: UUID,
    ): List<RoomPinEntity>

    @Modifying
    @Query("delete from RoomPinEntity p where p.id.userId = :userId")
    fun deleteAllOf(
        @Param("userId") userId: UUID,
    ): Int
}
