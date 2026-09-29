package com.drift.user.service

import com.drift.user.common.EventEnvelope
import com.drift.user.common.JwtSupport
import com.drift.user.config.AppProperties
import com.drift.user.exceptions.DomainError
import com.drift.user.interfaces.IAvatarUploader
import com.drift.user.interfaces.IEventPublisher
import com.drift.user.interfaces.IPresenceStore
import com.drift.user.interfaces.IUserService
import com.drift.user.models.NewUser
import com.drift.user.models.PresenceChangedPayload
import com.drift.user.models.SignUpResult
import com.drift.user.models.User
import com.drift.user.models.UserDeletedPayload
import com.drift.user.models.UserUpdatedPayload
import com.drift.user.repository.IUserRepository
import com.drift.user.utils.AvatarTypeValidator
import com.drift.user.utils.UsernameValidator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream
import java.time.Clock
import java.util.UUID

/**
 * User business logic (spec sections 3 and 13.1). Talks only to the domain ports
 * ([IUserRepository], [IPresenceStore], [IAvatarUploader], [IEventPublisher]) —
 * never to Spring Data, Valkey or Kafka directly (spec section 21 layering rule).
 */
@Service
class UserService(
    private val userRepository: IUserRepository,
    private val presenceStore: IPresenceStore,
    private val avatarUploader: IAvatarUploader,
    private val eventPublisher: IEventPublisher,
    private val jwtSupport: JwtSupport,
    private val properties: AppProperties,
    private val clock: Clock,
    private val meters: io.micrometer.core.instrument.MeterRegistry,
) : IUserService {
    /** Sign up: validates input, stores the avatar, saves the user, issues the session JWT. */
    @Transactional
    override fun signUp(
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

        // The id is chosen first so the avatar is stored under the real user id; that is how file-service finds it again
        // when this user expires (`user.deleted`).
        val userId = UUID.randomUUID()
        val avatarFileId =
            avatarUploader.uploadAvatar(
                ownerId = userId,
                fileName = avatarFileName ?: "avatar",
                contentType = avatarContentType ?: "application/octet-stream",
                content = avatarContent,
                sizeBytes = avatarSize,
            )

        val user =
            try {
                userRepository.save(
                    NewUser(
                        id = userId,
                        username = username,
                        name = name,
                        email = email,
                        avatarFileId = avatarFileId,
                        expiresAt = clock.instant().plus(properties.sessionTtl),
                    ),
                )
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                // Don't leave an orphaned avatar behind if the user could not be saved
                runCatching { avatarUploader.deleteAvatar(avatarFileId) }
                throw e
            }

        val token = jwtSupport.issue(user.id.toString(), user.username, properties.sessionTtl).serialize()
        return SignUpResult(user, token)
    }

    @Transactional(readOnly = true)
    override fun getById(id: UUID): User = userRepository.findById(id) ?: throw DomainError.NotFound("User not found.")

    @Transactional(readOnly = true)
    override fun getByIds(ids: Collection<UUID>): List<User> = userRepository.findAllByIds(ids)

    @Transactional(readOnly = true)
    override fun usernameAvailable(username: String): Boolean = !userRepository.existsByUsername(username)

    @Transactional
    override fun updateProfile(
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
    override fun heartbeat(userId: UUID) {
        presenceStore.heartbeat(userId, properties.presenceTtl)
    }

    /** Explicit presence change from message-service (socket connected / disconnected); publishes `presence.changed`. */
    override fun setPresence(
        userId: UUID,
        online: Boolean,
    ) {
        val wasOnline = presenceStore.isOnline(userId)
        if (online) presenceStore.heartbeat(userId, properties.presenceTtl) else presenceStore.remove(userId)
        if (wasOnline != online) {
            eventPublisher.publish(
                EventEnvelope.Companion.Topics.PRESENCE_CHANGED,
                EventEnvelope(
                    type = "presence.changed",
                    timestamp = clock.instant(),
                    payload = PresenceChangedPayload(userId.toString(), online),
                ),
            )
        }
    }

    override fun isOnline(userId: UUID): Boolean = presenceStore.isOnline(userId)

    override fun onlineStatus(ids: Collection<UUID>): Map<UUID, Boolean> = ids.associateWith { presenceStore.isOnline(it) }

    /** Cleanup (spec section 13.1): deletes expired users and publishes `user.deleted`. */
    @Transactional
    override fun deleteExpiredUsers(): List<UUID> {
        val deleted = userRepository.deleteExpired(clock.instant())
        deleted.forEach { id ->
            presenceStore.remove(id)
            eventPublisher.publish(
                EventEnvelope.Companion.Topics.USER_DELETED,
                EventEnvelope(type = "user.deleted", timestamp = clock.instant(), payload = UserDeletedPayload(id.toString())),
            )
        }
        meters.counter("drift.users.expired").increment(deleted.size.toDouble())
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

    companion object {
        const val MAX_NAME_LENGTH = 50
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
