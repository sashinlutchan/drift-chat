package com.drift.user.repository

import com.drift.user.domain.NewUser
import com.drift.user.domain.User
import com.drift.user.domain.UserRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/** Spring Data repository — used only by [JpaUserRepository], never by the service layer. */
interface SpringDataUserRepository : JpaRepository<UserEntity, UUID> {
    fun existsByUsername(username: String): Boolean
}

/** Adapter implementing the domain port [UserRepository]. */
@Repository
class JpaUserRepository(
    private val springData: SpringDataUserRepository,
) : UserRepository {
    override fun existsByUsername(username: String): Boolean = springData.existsByUsername(username)

    override fun save(user: NewUser): User {
        val entity = UserEntity.fromDomain(user)
        return springData.save(entity).toDomain()
    }

    override fun findById(id: UUID): User? = springData.findById(id).map(UserEntity::toDomain).orElse(null)

    override fun findAllByIds(ids: Collection<UUID>): List<User> = springData.findAllById(ids).map(UserEntity::toDomain)

    override fun updateProfile(
        id: UUID,
        name: String,
        email: String,
        avatarFileId: UUID?,
    ): User? {
        val entity = springData.findById(id).orElse(null) ?: return null
        entity.name = name
        entity.email = email
        entity.avatarFileId = avatarFileId
        entity.updatedAt = Instant.now()
        return springData.save(entity).toDomain()
    }

    override fun deleteExpired(expiresBefore: Instant): List<UUID> {
        val expired = springData.findAll().filter { it.expiresAt.isBefore(expiresBefore) }
        springData.deleteAll(expired)
        return expired.map { it.id }
    }
}
