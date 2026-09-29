package com.drift.user.dto

import com.drift.user.models.User
import java.time.Instant
import java.util.UUID

/** Public profile DTO — never contains the email of other users (spec section 3.5). */
data class UserDto(
    val id: UUID,
    val username: String,
    val name: String,
    val avatarFileId: UUID?,
    val online: Boolean,
) {
    companion object {
        fun from(
            user: User,
            online: Boolean,
        ) = UserDto(user.id, user.username, user.name, user.avatarFileId, online)
    }
}

/** Own profile DTO — includes email and session expiry (spec sections 3.2 and 3.4). */
data class OwnUserDto(
    val id: UUID,
    val username: String,
    val name: String,
    val email: String,
    val avatarFileId: UUID?,
    val online: Boolean,
    val sessionExpiresAt: Instant,
    val createdAt: Instant,
) {
    companion object {
        fun from(
            user: User,
            online: Boolean,
        ) = OwnUserDto(user.id, user.username, user.name, user.email, user.avatarFileId, online, user.expiresAt, user.createdAt)
    }
}

/** Response for the username availability check (spec section 14). */
data class UsernameCheckDto(
    val username: String,
    val available: Boolean,
)
