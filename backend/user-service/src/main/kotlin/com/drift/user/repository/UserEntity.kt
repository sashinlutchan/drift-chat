package com.drift.user.repository

import com.drift.user.domain.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * JPA entity for the `users` table (spec section 18). Regular class, not a data
 * class (spec section 21); equality based on id.
 */
@Entity
@Table(name = "users")
class UserEntity(
    @Id val id: UUID,
    @Column(nullable = false, unique = true, length = 20) var username: String,
    @Column(nullable = false, length = 50) var name: String,
    @Column(nullable = false) var email: String,
    @Column(name = "avatar_file_id") var avatarFileId: UUID?,
    @Column(name = "created_at", nullable = false) var createdAt: Instant,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant,
    @Column(name = "expires_at", nullable = false) var expiresAt: Instant,
) {
    override fun equals(other: Any?): Boolean = other is UserEntity && other.id == id

    override fun hashCode(): Int = id.hashCode()

    fun toDomain(): User =
        User(
            id = id,
            username = username,
            name = name,
            email = email,
            avatarFileId = avatarFileId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            expiresAt = expiresAt,
        )

    companion object {
        fun fromDomain(user: com.drift.user.domain.NewUser): UserEntity =
            UserEntity(
                id = UUID.randomUUID(),
                username = user.username,
                name = user.name,
                email = user.email,
                avatarFileId = user.avatarFileId,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                expiresAt = user.expiresAt,
            )
    }
}
