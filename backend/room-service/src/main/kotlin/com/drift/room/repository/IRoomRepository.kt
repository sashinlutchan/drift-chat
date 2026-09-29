package com.drift.room.repository

import com.drift.room.models.MembershipChange
import com.drift.room.models.NewRoom
import com.drift.room.models.Room
import java.time.Instant
import java.util.UUID

/** Repository port for rooms, members and pins (spec section 21 layering rule): services never see Spring Data. */
interface IRoomRepository {
    fun save(room: NewRoom): Room

    fun findById(id: UUID): Room?

    fun findBySlug(slug: String): Room?

    fun slugExists(slug: String): Boolean

    /** All rooms whose name contains [search] (case-insensitive); everything when blank. */
    fun search(search: String?): List<Room>

    /** Number of current members per room. */
    fun memberCounts(roomIds: Collection<UUID>): Map<UUID, Int>

    /** Current member ids per room. */
    fun memberIds(roomIds: Collection<UUID>): Map<UUID, List<UUID>>

    /** Which of [roomIds] the user is currently a member of. */
    fun joinedRooms(
        userId: UUID,
        roomIds: Collection<UUID>,
    ): Set<UUID>

    /** Which of [roomIds] the user was once a member of but has since left. */
    fun leftRooms(
        userId: UUID,
        roomIds: Collection<UUID>,
    ): Set<UUID>

    fun isMember(
        roomId: UUID,
        userId: UUID,
    ): Boolean

    /** Adds the user, or re-adds them after leaving. Returns `true` when membership actually changed. */
    fun join(
        roomId: UUID,
        userId: UUID,
        now: Instant,
    ): Boolean

    /** Marks the user as having left. Returns `true` when they were a member. */
    fun leave(
        roomId: UUID,
        userId: UUID,
        now: Instant,
    ): Boolean

    /** Pin timestamps for the user's pinned rooms. */
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

    /** Somebody is inside: clears `empty_since` and bumps `last_active_at`. */
    fun markOccupied(
        roomId: UUID,
        now: Instant,
    )

    /** Nobody is inside: starts the idle countdown if it isn't running already. */
    fun markEmpty(
        roomId: UUID,
        now: Instant,
    )

    /** Deletes rooms that have been empty since before [threshold] and returns them. */
    fun deleteEmptySince(threshold: Instant): List<Room>

    /** Removes the user from every room and clears their pins and authorship; returns the memberships that ended. */
    fun removeUser(userId: UUID): List<MembershipChange>
}
