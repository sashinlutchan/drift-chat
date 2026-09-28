package com.drift.user.domain

import java.time.Duration
import java.util.UUID

/**
 * Presence port (Valkey-backed, spec section 13.1): online/offline with heartbeat TTL.
 * Implemented in `persistence/` (Valkey access) behind this interface.
 */
interface PresenceStore {
    fun heartbeat(
        userId: UUID,
        ttl: Duration,
    )

    fun isOnline(userId: UUID): Boolean

    fun remove(userId: UUID)
}
