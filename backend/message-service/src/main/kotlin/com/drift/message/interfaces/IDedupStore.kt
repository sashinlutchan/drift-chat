package com.drift.message.interfaces

import java.time.Duration
import java.util.UUID

/** Remembers `clientId`s so a retried send never creates a second message (spec section 15). */
interface IDedupStore {
    /** Returns `null` if [candidate] was recorded for this client id, or the id recorded earlier if it was a retry. */
    fun claim(
        userId: UUID,
        clientId: String,
        candidate: UUID,
        ttl: Duration,
    ): UUID?
}
