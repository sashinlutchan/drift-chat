package com.drift.message.models

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

data class RoomOccupancyPayload(
    val roomId: String,
    val count: Int,
)

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

data class UserDeletedPayload(
    val userId: String,
)

data class UserUpdatedPayload(
    val userId: String,
    val username: String,
    val name: String,
    val avatarFileId: String?,
)

data class PresenceChangedPayload(
    val userId: String,
    val online: Boolean,
)
