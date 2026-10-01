package com.drift.room.interfaces

import com.drift.room.models.MemberProfile
import com.drift.room.models.Room
import com.drift.room.models.RoomInfo
import com.drift.room.models.RoomSummary
import java.util.UUID

interface IRoomService {
    fun list(
        userId: UUID,
        search: String?,
    ): List<RoomSummary>

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

    fun isMember(
        roomId: UUID,
        userId: UUID,
    ): Boolean?

    fun find(roomId: UUID): Room?

    fun onOccupancy(
        roomId: UUID,
        count: Int,
    )

    fun onUserDeleted(userId: UUID)

    fun deleteIdleRooms(): Int
}
