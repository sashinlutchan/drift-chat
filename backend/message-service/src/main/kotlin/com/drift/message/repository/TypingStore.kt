package com.drift.message.repository

import com.drift.message.interfaces.ITypingStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Valkey-backed [ITypingStore]: a hash `typing:{roomId}` of userId → expiry. Markers expire by themselves after a few
 * seconds, so a client that goes quiet stops "typing…" without sending anything. Changes are announced on a pub/sub
 * channel so every pod refreshes its own sockets.
 */
@Component
class TypingStore(
    private val redis: StringRedisTemplate,
) : ITypingStore {
    override fun start(
        roomId: UUID,
        userId: UUID,
        now: Instant,
    ) {
        redis.opsForHash<String, String>().put(key(roomId), userId.toString(), now.plus(MARKER_TTL).toEpochMilli().toString())
        redis.expire(key(roomId), KEY_TTL)
    }

    override fun stop(
        roomId: UUID,
        userId: UUID,
    ) {
        redis.opsForHash<String, String>().delete(key(roomId), userId.toString())
    }

    override fun active(
        roomId: UUID,
        now: Instant,
    ): List<UUID> =
        redis
            .opsForHash<String, String>()
            .entries(key(roomId))
            .filterValues { it.toLong() > now.toEpochMilli() }
            .keys
            .map(UUID::fromString)

    override fun announce(roomId: UUID) {
        redis.convertAndSend(CHANNEL, roomId.toString())
    }

    private fun key(roomId: UUID) = "typing:$roomId"

    companion object {
        const val CHANNEL = "typing:updates"
        private val MARKER_TTL: Duration = Duration.ofSeconds(6)
        private val KEY_TTL: Duration = Duration.ofSeconds(60)
    }
}
