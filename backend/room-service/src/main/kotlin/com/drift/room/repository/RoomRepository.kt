package com.drift.room.repository

import com.drift.room.models.MembershipChange
import com.drift.room.models.NewRoom
import com.drift.room.models.Room
import com.drift.room.models.RoomEntity
import com.drift.room.models.RoomMemberEntity
import com.drift.room.models.RoomMemberId
import com.drift.room.models.RoomPinEntity
import com.drift.room.models.RoomPinId
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Repository
class RoomRepository(
    private val rooms: DataRepository,
    private val members: MembersDataRepository,
    private val pins: PinsDataRepository,
    private val clock: Clock,
) : IRoomRepository {
    @Transactional
    override fun save(room: NewRoom): Room {
        val now = clock.instant()
        return rooms.save(RoomEntity(UUID.randomUUID(), room.name, room.slug, room.createdBy, now, now, now)).toRoom()
    }

    override fun findById(id: UUID): Room? = rooms.findById(id).map(RoomEntity::toRoom).orElse(null)

    override fun findBySlug(slug: String): Room? = rooms.findBySlug(slug)?.toRoom()

    override fun slugExists(slug: String): Boolean = rooms.existsBySlug(slug)

    override fun search(search: String?): List<Room> {
        val term = search?.trim()?.takeIf { it.isNotEmpty() }
        return (if (term == null) rooms.findAll() else rooms.search(term)).map(RoomEntity::toRoom)
    }

    override fun memberCounts(roomIds: Collection<UUID>): Map<UUID, Int> = memberIds(roomIds).mapValues { it.value.size }

    override fun memberIds(roomIds: Collection<UUID>): Map<UUID, List<UUID>> =
        if (roomIds.isEmpty()) {
            emptyMap()
        } else {
            members.activeIn(roomIds).groupBy({ it.id.roomId }, { it.id.userId })
        }

    override fun joinedRooms(
        userId: UUID,
        roomIds: Collection<UUID>,
    ): Set<UUID> =
        members
            .activeOf(userId)
            .map { it.id.roomId }
            .filter { it in roomIds }
            .toSet()

    override fun leftRooms(
        userId: UUID,
        roomIds: Collection<UUID>,
    ): Set<UUID> =
        members
            .leftOf(userId)
            .map { it.id.roomId }
            .filter { it in roomIds }
            .toSet()

    override fun isMember(
        roomId: UUID,
        userId: UUID,
    ): Boolean = members.findById(RoomMemberId(roomId, userId)).map { it.leftAt == null }.orElse(false)

    @Transactional
    override fun join(
        roomId: UUID,
        userId: UUID,
        now: Instant,
    ): Boolean {
        val existing = members.findById(RoomMemberId(roomId, userId)).orElse(null)
        return when {
            existing == null -> {
                members.save(RoomMemberEntity(RoomMemberId(roomId, userId), now, null))
                true
            }
            existing.leftAt != null -> {
                existing.leftAt = null
                existing.joinedAt = now
                members.save(existing)
                true
            }
            else -> false
        }
    }

    @Transactional
    override fun leave(
        roomId: UUID,
        userId: UUID,
        now: Instant,
    ): Boolean {
        val existing = members.findById(RoomMemberId(roomId, userId)).orElse(null)
        if (existing == null || existing.leftAt != null) return false
        existing.leftAt = now
        members.save(existing)
        return true
    }

    override fun pins(userId: UUID): Map<UUID, Instant> = pins.ofUser(userId).associate { it.id.roomId to it.pinnedAt }

    @Transactional
    override fun pin(
        roomId: UUID,
        userId: UUID,
        now: Instant,
    ) {
        val id = RoomPinId(userId, roomId)
        if (!pins.existsById(id)) pins.save(RoomPinEntity(id, now))
    }

    @Transactional
    override fun unpin(
        roomId: UUID,
        userId: UUID,
    ) {
        pins.deleteById(RoomPinId(userId, roomId))
    }

    @Transactional
    override fun markOccupied(
        roomId: UUID,
        now: Instant,
    ) {
        rooms.occupied(roomId, now)
    }

    @Transactional
    override fun markEmpty(
        roomId: UUID,
        now: Instant,
    ) {
        rooms.empty(roomId, now)
    }

    @Transactional
    override fun deleteEmptySince(threshold: Instant): List<Room> {
        val expired = rooms.findByEmptySinceBefore(threshold)
        rooms.deleteAll(expired)
        return expired.map(RoomEntity::toRoom)
    }

    @Transactional
    override fun removeUser(userId: UUID): List<MembershipChange> {
        val ended = members.activeOf(userId).map { MembershipChange(it.id.roomId, userId) }
        members.deleteAllOf(userId)
        pins.deleteAllOf(userId)
        rooms.clearAuthor(userId)
        return ended
    }
}
