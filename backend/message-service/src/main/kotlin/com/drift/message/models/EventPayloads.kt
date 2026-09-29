package com.drift.message.models

/** `message.created`: the message with its content still encrypted (spec section 17). */
data class MessageCreatedPayload(
    val id: String,
    val roomId: String,
    val senderId: String?,
    val type: String,
    val ciphertext: String,
    val nonce: String,
    val keyVersion: Int,
    val fileId: String?,
    val createdAt: String,
)

/** `room.occupancy`: how many distinct users currently have the room open. */
data class RoomOccupancyPayload(
    val roomId: String,
    val count: Int,
)

/** `room.created` / `room.updated`. */
data class RoomEventPayload(
    val roomId: String,
    val name: String,
    val slug: String,
    val memberCount: Int,
)

/** `room.deleted`. */
data class RoomDeletedPayload(
    val roomId: String,
)

/** `member.joined` / `member.left`. */
data class MemberEventPayload(
    val roomId: String,
    val userId: String,
)

/** `user.deleted`. */
data class UserDeletedPayload(
    val userId: String,
)

/** `user.updated`. */
data class UserUpdatedPayload(
    val userId: String,
    val username: String,
    val name: String,
    val avatarFileId: String?,
)

/** `presence.changed`. */
data class PresenceChangedPayload(
    val userId: String,
    val online: Boolean,
)
