package com.drift.user.models

import java.time.Instant
import java.util.UUID

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
