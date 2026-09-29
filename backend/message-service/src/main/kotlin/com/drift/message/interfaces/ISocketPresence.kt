package com.drift.message.interfaces

import java.time.Instant
import java.util.UUID

/** Which sockets a user has open across all pods, so "offline" only happens when the last one closes. */
interface ISocketPresence {
    /** Returns `true` when this is the user's first live socket. */
    fun connect(
        userId: UUID,
        sessionId: String,
        now: Instant,
    ): Boolean

    /** Returns `true` when that was the user's last live socket. */
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
