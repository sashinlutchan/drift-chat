package com.drift.user.models

data class SignUpResult(
    val user: User,
    val sessionJwt: String,
)

data class PresenceChangedPayload(
    val userId: String,
    val online: Boolean,
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
