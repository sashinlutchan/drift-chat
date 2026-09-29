package com.drift.user.repository

import com.drift.user.models.NewUser
import com.drift.user.models.User
import java.time.Instant
import java.util.UUID

/**
 * Repository port for users (spec section 21 layering rule): the service layer
 * depends on this interface, never on Spring Data directly. Implemented in `persistence/`.
 */
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
