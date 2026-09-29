package com.drift.room.models

/** `room.created` / `room.updated` payload: what the lobby needs to redraw a card (spec section 17). */
data class RoomEventPayload(
    val roomId: String,
    val name: String,
    val slug: String,
    val memberCount: Int,
)

/** `room.deleted` payload. */
data class RoomDeletedPayload(
    val roomId: String,
)

/** `member.joined` / `member.left` payload. */
data class MemberEventPayload(
    val roomId: String,
    val userId: String,
)

/** `room.occupancy` payload from message-service: how many users currently have the room open. */
data class RoomOccupancyPayload(
    val roomId: String,
    val count: Int,
)

/** `user.deleted` payload from user-service. */
data class UserDeletedPayload(
    val userId: String,
)
