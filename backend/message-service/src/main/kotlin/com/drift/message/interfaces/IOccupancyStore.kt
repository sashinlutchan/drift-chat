package com.drift.message.interfaces

import java.time.Instant
import java.util.UUID

/** Who has which room open, across every pod (Valkey `room:occupancy:{roomId}`, spec section 18). */
interface IOccupancyStore {
    /** Records a socket entering a room; returns how many distinct users are now inside. */
    fun enter(
        roomId: UUID,
        userId: UUID,
        sessionId: String,
        now: Instant,
    ): Int

    /** Records a socket leaving a room; returns how many distinct users are still inside. */
    fun exit(
        roomId: UUID,
        userId: UUID,
        sessionId: String,
        now: Instant,
    ): Int

    /** Heartbeat: keeps a socket's entry from going stale. */
    fun touch(
        roomId: UUID,
        userId: UUID,
        sessionId: String,
        now: Instant,
    )

    /** Removes entries whose socket stopped heartbeating (for example a crashed pod); returns rooms whose count changed. */
    fun sweep(now: Instant): Map<UUID, Int>
}
