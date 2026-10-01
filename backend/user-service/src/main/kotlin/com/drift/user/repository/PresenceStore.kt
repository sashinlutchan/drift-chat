package com.drift.user.repository

import com.drift.user.interfaces.IPresenceStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
class PresenceStore(
    private val redis: StringRedisTemplate,
) : IPresenceStore {
    override fun heartbeat(
        userId: UUID,
        ttl: Duration,
    ) {
        redis.opsForValue().set(key(userId), "online", ttl)
    }

    override fun isOnline(userId: UUID): Boolean = redis.hasKey(key(userId)) == true

    override fun remove(userId: UUID) {
        redis.delete(key(userId))
    }

    private fun key(userId: UUID) = "presence:$userId"
}
