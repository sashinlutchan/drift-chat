package com.drift.message.interfaces

import java.time.Instant
import java.util.UUID

interface IOccupancyStore {
    fun enter(
        roomId: UUID,
        userId: UUID,
        sessionId: String,
        now: Instant,
    ): Int

    fun exit(
        roomId: UUID,
        userId: UUID,
        sessionId: String,
        now: Instant,
    ): Int

    fun touch(
        roomId: UUID,
        userId: UUID,
        sessionId: String,
        now: Instant,
    )

    fun sweep(now: Instant): Map<UUID, Int>
}
