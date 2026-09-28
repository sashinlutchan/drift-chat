package com.drift.user.domain

import java.time.Instant
import java.util.UUID

/** Domain model for a user — no Spring/JPA annotations (spec section 21). */
data class User(
    val id: UUID,
    val username: String,
    val name: String,
    val email: String,
    val avatarFileId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant,
) {
    fun isExpired(now: Instant): Boolean = !expiresAt.isAfter(now)
}
