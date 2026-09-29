package com.drift.message.service

import com.drift.message.dto.MessageDto
import com.drift.message.dto.SenderDto
import com.drift.message.interfaces.IMessageRenderer
import com.drift.message.interfaces.IUserDirectory
import com.drift.message.models.Message
import com.drift.message.models.UserProfile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns messages into what clients see, filling in the sender from user-service. Profiles are cached for a short
 * time (spec section 18) and dropped when a `user.updated` / `user.deleted` event arrives.
 */
@Component
class MessageRenderer(
    private val users: IUserDirectory,
    private val clock: Clock,
) : IMessageRenderer {
    private data class Cached(
        val profile: UserProfile?,
        val expiresAt: Instant,
    )

    private val cache = ConcurrentHashMap<UUID, Cached>()

    override fun render(messages: List<Message>): List<MessageDto> {
        val senders = messages.mapNotNull { it.senderId }.distinct()
        val profiles = profilesFor(senders)
        return messages.map { message ->
            val sender =
                when {
                    message.senderDeleted -> DELETED_SENDER
                    message.senderId == null -> null
                    else ->
                        profiles[message.senderId]?.let { SenderDto(it.id, it.name, it.username, it.avatarFileId, false) }
                            ?: DELETED_SENDER
                }
            MessageDto(message.id, message.roomId, message.type.dbValue, message.text, message.fileId, sender, message.createdAt)
        }
    }

    override fun senders(ids: Collection<UUID>): List<SenderDto> =
        profilesFor(ids.distinct()).values.map { SenderDto(it.id, it.name, it.username, it.avatarFileId, false) }

    override fun invalidate(userId: UUID) {
        cache.remove(userId)
    }

    private fun profilesFor(ids: List<UUID>): Map<UUID, UserProfile> {
        val now = clock.instant()
        val fresh = ids.filter { cache[it]?.expiresAt?.isAfter(now) != true }
        if (fresh.isNotEmpty()) {
            val loaded = users.profiles(fresh)
            fresh.forEach { cache[it] = Cached(loaded[it], now.plus(TTL)) }
        }
        return ids.mapNotNull { id -> cache[id]?.profile?.let { id to it } }.toMap()
    }

    private companion object {
        val TTL: Duration = Duration.ofSeconds(60)
        val DELETED_SENDER = SenderDto(null, "Deleted user", null, null, true)
    }
}
