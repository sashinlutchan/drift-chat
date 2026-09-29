package com.drift.room.interfaces

import com.drift.room.models.MemberProfile
import com.drift.room.models.Room
import com.drift.room.models.RoomInfo
import com.drift.room.models.RoomSummary
import java.util.UUID

/** Room business logic (spec sections 5–8 and 13.2); implemented by `service/RoomService`. */
interface IRoomService {
    /** Every room, pinned first (most recently pinned on top), then most recently active first. */
    fun list(
        userId: UUID,
        search: String?,
    ): List<RoomSummary>

    /** Creates the room; the creator becomes its first member. */
    fun create(
        userId: UUID,
        name: String,
    ): RoomSummary

    fun info(
        roomId: UUID,
        userId: UUID,
    ): RoomInfo

    fun bySlug(
        slug: String,
        userId: UUID,
    ): RoomSummary

    fun members(roomId: UUID): List<MemberProfile>

    /** Join or rejoin; joining a room you're already in is a no-op. */
    fun join(
        roomId: UUID,
        userId: UUID,
    ): RoomSummary

    fun leave(
        roomId: UUID,
        userId: UUID,
    )

    fun pin(
        roomId: UUID,
        userId: UUID,
    )

    fun unpin(
        roomId: UUID,
        userId: UUID,
    )

    /** `null` when the room doesn't exist, otherwise whether the user is a current member. */
    fun isMember(
        roomId: UUID,
        userId: UUID,
    ): Boolean?

    fun find(roomId: UUID): Room?

    /** From message-service: how many users currently have the room open. */
    fun onOccupancy(
        roomId: UUID,
        count: Int,
    )

    /** From user-service: the user is gone, so are their memberships and pins. */
    fun onUserDeleted(userId: UUID)

    /** Deletes rooms empty longer than the idle TTL and announces it; returns how many. */
    fun deleteIdleRooms(): Int
}
