package com.drift.room.models

import java.time.Instant
import java.util.UUID

/** Domain model for a room — no Spring/JPA annotations (spec section 21). */
data class Room(
    val id: UUID,
    val name: String,
    val slug: String,
    val createdBy: UUID?,
    val createdAt: Instant,
    val lastActiveAt: Instant,
    val emptySince: Instant?,
)

/** Data needed to create a room. */
data class NewRoom(
    val name: String,
    val slug: String,
    val createdBy: UUID,
)

/** A room as one user sees it in the lobby (spec section 5.1). */
data class RoomSummary(
    val room: Room,
    val memberCount: Int,
    val onlineCount: Int,
    val joined: Boolean,
    /** The user was a member before and left, so the card offers "Rejoin" (spec section 5.2). */
    val left: Boolean,
    val pinnedAt: Instant?,
)

/** A member with their public profile and presence (spec section 7.6). */
data class MemberProfile(
    val userId: UUID,
    val username: String,
    val name: String,
    val avatarFileId: UUID?,
    val online: Boolean,
)

/** Public profile fields room-service needs from user-service. */
data class UserProfile(
    val id: UUID,
    val username: String,
    val name: String,
    val avatarFileId: UUID?,
    val online: Boolean,
)

/** Everything the room-info panel shows (spec section 7.7). */
data class RoomInfo(
    val summary: RoomSummary,
    val createdBy: UserProfile?,
    val idleDeleteAt: Instant?,
)

/** Result of a cleanup or user-removal step that other services must hear about. */
data class MembershipChange(
    val roomId: UUID,
    val userId: UUID,
)
