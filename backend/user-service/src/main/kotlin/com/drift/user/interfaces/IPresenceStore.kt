package com.drift.user.interfaces

import java.time.Duration
import java.util.UUID

interface IPresenceStore {
    fun heartbeat(
        userId: UUID,
        ttl: Duration,
    )

    fun isOnline(userId: UUID): Boolean

    fun remove(userId: UUID)
}
