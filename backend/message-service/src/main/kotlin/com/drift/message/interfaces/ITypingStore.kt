package com.drift.message.interfaces

import java.time.Instant
import java.util.UUID

interface ITypingStore {
    fun start(
        roomId: UUID,
        userId: UUID,
        now: Instant,
    )

    fun stop(
        roomId: UUID,
        userId: UUID,
    )

    fun active(
        roomId: UUID,
        now: Instant,
    ): List<UUID>

    fun announce(roomId: UUID)
}
