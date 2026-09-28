package com.drift.user.service

import com.drift.user.common.DomainError
import com.drift.user.common.EventEnvelope
import com.drift.user.common.JwtSupport
import com.drift.user.config.AppProperties
import com.drift.user.domain.AvatarUploader
import com.drift.user.domain.EventPublisher
import com.drift.user.domain.NewUser
import com.drift.user.domain.PresenceStore
import com.drift.user.domain.User
import com.drift.user.domain.UserRepository
import com.drift.user.utils.AvatarTypeValidator
import com.drift.user.utils.UsernameValidator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream
import java.time.Clock
import java.util.UUID

/**
 * User business logic (spec sections 3 and 13.1). Talks only to the domain ports
 * ([UserRepository], [PresenceStore], [AvatarUploader], [EventPublisher]) —
 * never to Spring Data, Valkey or Kafka directly (spec section 21 layering rule).
 */
@Service
class UserService(
    private val userRepository: UserRepository,
    private val presenceStore: PresenceStore,
    private val avatarUploader: AvatarUploader,
    private val eventPublisher: EventPublisher,
    private val jwtSupport: JwtSupport,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    /** Sign up: validates input, stores the avatar, saves the user, issues the session JWT. */
    @Transactional
    fun signUp(
        username: String,
        name: String,
        email: String,
        avatarFileName: String?,
        avatarContentType: String?,
        avatarContent: InputStream,
        avatarSize: Long,
    ): SignUpResult {
        if (!UsernameValidator.isValid(username)) {
            throw DomainError.UsernameInvalid(
                "Username must be ${UsernameValidator.MIN_LENGTH}-${UsernameValidator.MAX_LENGTH} characters, letters, numbers, _ and - only.",
            )
        }
        if (name.isBlank() || name.length > MAX_NAME_LENGTH) {
            throw DomainError.ValidationFailed("Name must be 1-$MAX_NAME_LENGTH characters.")
        }
        if (!EMAIL_PATTERN.matches(email)) {
            throw DomainError.ValidationFailed("Email must be a valid email address.")
        }
        if (!AvatarTypeValidator.isAllowed(avatarContentType)) {
            throw DomainError.UnsupportedMediaType("Avatar must be a JPEG, PNG or WebP image.")
        }
        if (userRepository.existsByUsername(username)) {
            throw DomainError.UsernameTaken("This username is taken.")
        }

        val avatarFileId =
            avatarUploader.uploadAvatar(
                ownerId = UUID.nameUUIDFromBytes(username.toByteArray()),
                fileName = avatarFileName ?: "avatar",
                contentType = avatarContentType ?: "application/octet-stream",
                content = avatarContent,
                sizeBytes = avatarSize,
            )

        val user =
            userRepository.save(
                NewUser(
                    username = username,
                    name = name,
                    email = email,
                    avatarFileId = avatarFileId,
                    expiresAt = clock.instant().plus(properties.sessionTtl),
                ),
            )

        val token = jwtSupport.issue(user.id.toString(), user.username, properties.sessionTtl).serialize()
        return SignUpResult(user, token)
    }

    @Transactional(readOnly = true)
    fun getById(id: UUID): User = userRepository.findById(id) ?: throw DomainError.NotFound("User not found.")

    @Transactional(readOnly = true)
    fun getByIds(ids: Collection<UUID>): List<User> = userRepository.findAllByIds(ids)

    @Transactional(readOnly = true)
    fun usernameAvailable(username: String): Boolean = !userRepository.existsByUsername(username)

    @Transactional
    fun updateProfile(
        id: UUID,
        name: String?,
        email: String?,
        avatarFileName: String?,
        avatarContentType: String?,
        avatarContent: InputStream?,
        avatarSize: Long,
    ): User {
        val existing = userRepository.findById(id) ?: throw DomainError.NotFound("User not found.")

        var avatarFileId = existing.avatarFileId
        if (avatarContent != null) {
            if (!AvatarTypeValidator.isAllowed(avatarContentType)) {
                throw DomainError.UnsupportedMediaType("Avatar must be a JPEG, PNG or WebP image.")
            }
            avatarFileId =
                avatarUploader.uploadAvatar(
                    ownerId = id,
                    fileName = avatarFileName ?: "avatar",
                    contentType = avatarContentType ?: "application/octet-stream",
                    content = avatarContent,
                    sizeBytes = avatarSize,
                )
            existing.avatarFileId?.let(avatarUploader::deleteAvatar)
        }

        val updated =
            userRepository.updateProfile(
                id = id,
                name = name ?: existing.name,
                email = email ?: existing.email,
                avatarFileId = avatarFileId,
            ) ?: throw DomainError.NotFound("User not found.")

        publishUserUpdated(updated)
        return updated
    }

    /** Presence heartbeat (spec section 4): keeps the user online for PRESENCE_TTL. */
    fun heartbeat(userId: UUID) {
        presenceStore.heartbeat(userId, properties.presenceTtl)
    }

    fun isOnline(userId: UUID): Boolean = presenceStore.isOnline(userId)

    fun onlineStatus(ids: Collection<UUID>): Map<UUID, Boolean> = ids.associateWith { presenceStore.isOnline(it) }

    /** Cleanup (spec section 13.1): deletes expired users and publishes `user.deleted`. */
    @Transactional
    fun deleteExpiredUsers(): List<UUID> {
        val deleted = userRepository.deleteExpired(clock.instant())
        deleted.forEach { id ->
            presenceStore.remove(id)
            eventPublisher.publish(
                EventEnvelope.Companion.Topics.USER_DELETED,
                EventEnvelope(type = "user.deleted", timestamp = clock.instant(), payload = UserDeletedPayload(id.toString())),
            )
        }
        return deleted
    }

    private fun publishUserUpdated(user: User) {
        eventPublisher.publish(
            EventEnvelope.Companion.Topics.USER_UPDATED,
            EventEnvelope(
                type = "user.updated",
                timestamp = clock.instant(),
                payload = UserUpdatedPayload(user.id.toString(), user.username, user.name, user.avatarFileId?.toString()),
            ),
        )
    }

    data class SignUpResult(
        val user: User,
        val sessionJwt: String,
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

    companion object {
        const val MAX_NAME_LENGTH = 50
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
