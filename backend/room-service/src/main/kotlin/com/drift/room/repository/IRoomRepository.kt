package com.drift.room.repository

import com.drift.room.models.MembershipChange
import com.drift.room.models.NewRoom
import com.drift.room.models.Room
import java.time.Instant
import java.util.UUID

interface IRoomRepository {
    fun save(room: NewRoom): Room

    fun findById(id: UUID): Room?

    fun findBySlug(slug: String): Room?

    fun slugExists(slug: String): Boolean

    fun search(search: String?): List<Room>

    fun memberCounts(roomIds: Collection<UUID>): Map<UUID, Int>

    fun memberIds(roomIds: Collection<UUID>): Map<UUID, List<UUID>>

    fun joinedRooms(
        userId: UUID,
        roomIds: Collection<UUID>,
    ): Set<UUID>

    fun leftRooms(
        userId: UUID,
        roomIds: Collection<UUID>,
    ): Set<UUID>

    fun isMember(
        roomId: UUID,
        userId: UUID,
    ): Boolean

    fun join(
        roomId: UUID,
        userId: UUID,
        now: Instant,
    ): Boolean

    fun leave(
        roomId: UUID,
        userId: UUID,
        now: Instant,
    ): Boolean

    fun pins(userId: UUID): Map<UUID, Instant>

    fun pin(
        roomId: UUID,
        userId: UUID,
        now: Instant,
    )

    fun unpin(
        roomId: UUID,
        userId: UUID,
    )

    fun markOccupied(
        roomId: UUID,
        now: Instant,
    )

    fun markEmpty(
        roomId: UUID,
        now: Instant,
    )

    fun deleteEmptySince(threshold: Instant): List<Room>

    fun removeUser(userId: UUID): List<MembershipChange>
}
