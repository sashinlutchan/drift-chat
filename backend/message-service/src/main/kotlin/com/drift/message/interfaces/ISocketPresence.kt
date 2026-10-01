package com.drift.message.interfaces

import java.time.Instant
import java.util.UUID

interface ISocketPresence {
    fun connect(
        userId: UUID,
        sessionId: String,
        now: Instant,
    ): Boolean

    fun disconnect(
        userId: UUID,
        sessionId: String,
        now: Instant,
    ): Boolean

    fun touch(
        userId: UUID,
        sessionId: String,
        now: Instant,
    )
}
