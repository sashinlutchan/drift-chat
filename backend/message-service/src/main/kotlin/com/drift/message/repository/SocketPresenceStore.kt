package com.drift.message.repository

import com.drift.message.config.AppProperties
import com.drift.message.interfaces.ISocketPresence
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/** Valkey-backed [ISocketPresence]: a sorted set `user:sockets:{userId}` of session ids scored by last heartbeat. */
@Component
class SocketPresenceStore(
    private val redis: StringRedisTemplate,
    private val properties: AppProperties,
) : ISocketPresence {
    override fun connect(
        userId: UUID,
        sessionId: String,
        now: Instant,
    ): Boolean {
        val first = live(userId, now) == 0
        touch(userId, sessionId, now)
        return first
    }

    override fun disconnect(
        userId: UUID,
        sessionId: String,
        now: Instant,
    ): Boolean {
        redis.opsForZSet().remove(key(userId), sessionId)
        return live(userId, now) == 0
    }

    override fun touch(
        userId: UUID,
        sessionId: String,
        now: Instant,
    ) {
        redis.opsForZSet().add(key(userId), sessionId, now.toEpochMilli().toDouble())
        redis.expire(key(userId), properties.socketStaleAfter.multipliedBy(2))
    }

    private fun live(
        userId: UUID,
        now: Instant,
    ): Int =
        redis
            .opsForZSet()
            .count(key(userId), now.minus(properties.socketStaleAfter).toEpochMilli().toDouble(), Double.POSITIVE_INFINITY)
            ?.toInt() ?: 0

    private fun key(userId: UUID) = "user:sockets:$userId"
}
