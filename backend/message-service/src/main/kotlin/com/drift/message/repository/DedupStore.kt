package com.drift.message.repository

import com.drift.message.interfaces.IDedupStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/** Valkey-backed [IDedupStore]: `SET NX` on `msg:client:{userId}:{clientId}`. */
@Component
class DedupStore(
    private val redis: StringRedisTemplate,
) : IDedupStore {
    override fun claim(
        userId: UUID,
        clientId: String,
        candidate: UUID,
        ttl: Duration,
    ): UUID? {
        val key = "msg:client:$userId:$clientId"
        if (redis.opsForValue().setIfAbsent(key, candidate.toString(), ttl) == true) return null
        return redis.opsForValue().get(key)?.let(UUID::fromString)
    }
}
