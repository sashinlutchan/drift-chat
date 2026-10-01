package com.drift.user.models

import java.time.Instant
import java.util.UUID

data class NewUser(
    val id: UUID,
    val username: String,
    val name: String,
    val email: String,
    val avatarFileId: UUID?,
    val expiresAt: Instant,
)
