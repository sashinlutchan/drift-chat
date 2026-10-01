package com.drift.room.models

data class RoomEventPayload(
    val roomId: String,
    val name: String,
    val slug: String,
    val memberCount: Int,
)

data class RoomDeletedPayload(
    val roomId: String,
)

data class MemberEventPayload(
    val roomId: String,
    val userId: String,
)

data class RoomOccupancyPayload(
    val roomId: String,
    val count: Int,
)

data class UserDeletedPayload(
    val userId: String,
)
