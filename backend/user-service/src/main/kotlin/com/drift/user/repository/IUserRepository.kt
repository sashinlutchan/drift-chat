package com.drift.user.repository

import com.drift.user.models.NewUser
import com.drift.user.models.User
import java.time.Instant
import java.util.UUID

interface IUserRepository {
    fun existsByUsername(username: String): Boolean

    fun save(user: NewUser): User

    fun findById(id: UUID): User?

    fun findAllByIds(ids: Collection<UUID>): List<User>

    fun updateProfile(
        id: UUID,
        name: String,
        email: String,
        avatarFileId: UUID?,
    ): User?

    fun deleteExpired(expiresBefore: Instant): List<UUID>
}
