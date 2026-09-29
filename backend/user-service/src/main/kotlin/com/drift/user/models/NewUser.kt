package com.drift.user.models

import java.time.Instant
import java.util.UUID

/** Data needed to create a user. The id is chosen up front so the avatar can be stored under the real user id. */
data class NewUser(
    val id: UUID,
    val username: String,
    val name: String,
    val email: String,
    val avatarFileId: UUID?,
    val expiresAt: Instant,
)
