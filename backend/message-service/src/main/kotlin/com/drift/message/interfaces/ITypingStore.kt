package com.drift.message.interfaces

import java.time.Instant
import java.util.UUID

/** Short-lived typing markers (Valkey `typing:{roomId}`, spec section 18) and the bus that tells every pod about changes. */
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

    /** Users currently typing (markers expire on their own if the client goes quiet). */
    fun active(
        roomId: UUID,
        now: Instant,
    ): List<UUID>

    /** Tells every pod that typing changed in a room so each can update its own sockets. */
    fun announce(roomId: UUID)
}
