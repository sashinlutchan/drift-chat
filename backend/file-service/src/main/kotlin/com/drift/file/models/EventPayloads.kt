package com.drift.file.models

/** `room.deleted` event payload (spec section 17). */
data class RoomDeletedPayload(
    val roomId: String,
)

/** `user.deleted` event payload (spec section 17). */
data class UserDeletedPayload(
    val userId: String,
)
