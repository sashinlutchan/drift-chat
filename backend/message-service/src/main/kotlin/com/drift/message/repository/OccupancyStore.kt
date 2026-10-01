package com.drift.message.repository

import com.drift.message.config.AppProperties
import com.drift.message.interfaces.IOccupancyStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class OccupancyStore(
    private val redis: StringRedisTemplate,
    private val properties: AppProperties,
) : IOccupancyStore {
    override fun enter(
        roomId: UUID,
        userId: UUID,
        sessionId: String,
        now: Instant,
    ): Int {
        redis.opsForZSet().add(key(roomId), member(userId, sessionId), now.toEpochMilli().toDouble())
        redis.opsForSet().add(ROOMS, roomId.toString())
        return liveUsers(roomId, now)
    }

    override fun exit(
        roomId: UUID,
        userId: UUID,
        sessionId: String,
        now: Instant,
    ): Int {
        redis.opsForZSet().remove(key(roomId), member(userId, sessionId))
        if ((redis.opsForZSet().zCard(key(roomId)) ?: 0L) == 0L) redis.opsForSet().remove(ROOMS, roomId.toString())
        return liveUsers(roomId, now)
    }

    override fun touch(
        roomId: UUID,
        userId: UUID,
        sessionId: String,
        now: Instant,
    ) {
        redis.opsForZSet().add(key(roomId), member(userId, sessionId), now.toEpochMilli().toDouble())
    }

    override fun sweep(now: Instant): Map<UUID, Int> {
        val changed = mutableMapOf<UUID, Int>()
        val cutoff = cutoff(now)
        redis.opsForSet().members(ROOMS).orEmpty().forEach { rawId ->
            val roomId = UUID.fromString(rawId)
            val removed = redis.opsForZSet().removeRangeByScore(key(roomId), Double.NEGATIVE_INFINITY, cutoff) ?: 0L
            if ((redis.opsForZSet().zCard(key(roomId)) ?: 0L) == 0L) redis.opsForSet().remove(ROOMS, rawId)
            if (removed > 0) changed[roomId] = liveUsers(roomId, now)
        }
        return changed
    }

    private fun liveUsers(
        roomId: UUID,
        now: Instant,
    ): Int =
        redis
            .opsForZSet()
            .rangeByScore(key(roomId), cutoff(now), Double.POSITIVE_INFINITY)
            .orEmpty()
            .map { it.substringBefore('|') }
            .toSet()
            .size

    private fun cutoff(now: Instant): Double = now.minus(properties.socketStaleAfter).toEpochMilli().toDouble()

    private fun key(roomId: UUID) = "room:occupancy:$roomId"

    private fun member(
        userId: UUID,
        sessionId: String,
    ) = "$userId|$sessionId"

    private companion object {
        const val ROOMS = "rooms:occupied"
    }
}
