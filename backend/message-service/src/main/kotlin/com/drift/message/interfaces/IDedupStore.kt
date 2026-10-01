package com.drift.message.interfaces

import java.time.Duration
import java.util.UUID

interface IDedupStore {
    fun claim(
        userId: UUID,
        clientId: String,
        candidate: UUID,
        ttl: Duration,
    ): UUID?
}
