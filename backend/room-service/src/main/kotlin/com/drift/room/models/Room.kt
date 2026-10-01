package com.drift.room.models

import java.time.Instant
import java.util.UUID

data class Room(
    val id: UUID,
    val name: String,
    val slug: String,
    val createdBy: UUID?,
    val createdAt: Instant,
    val lastActiveAt: Instant,
    val emptySince: Instant?,
)

data class NewRoom(
    val name: String,
    val slug: String,
    val createdBy: UUID,
)

data class RoomSummary(
    val room: Room,
    val memberCount: Int,
    val onlineCount: Int,
    val joined: Boolean,
    val left: Boolean,
    val pinnedAt: Instant?,
)

data class MemberProfile(
    val userId: UUID,
    val username: String,
    val name: String,
    val avatarFileId: UUID?,
    val online: Boolean,
)

data class UserProfile(
    val id: UUID,
    val username: String,
    val name: String,
    val avatarFileId: UUID?,
    val online: Boolean,
)

data class RoomInfo(
    val summary: RoomSummary,
    val createdBy: UserProfile?,
    val idleDeleteAt: Instant?,
)

data class MembershipChange(
    val roomId: UUID,
    val userId: UUID,
)
