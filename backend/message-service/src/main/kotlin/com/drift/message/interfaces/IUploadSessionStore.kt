package com.drift.message.interfaces

import com.drift.message.models.UploadSession
import java.time.Duration

/** Upload sessions and which chunks arrived (Valkey `upload:{uploadId}`, spec sections 13.6 and 18). */
interface IUploadSessionStore {
    fun create(
        session: UploadSession,
        ttl: Duration,
    )

    fun find(id: String): UploadSession?

    /** Records that a chunk arrived and keeps the session alive. */
    fun markChunk(
        id: String,
        index: Int,
        ttl: Duration,
    )

    fun received(id: String): Set<Int>

    fun delete(id: String)
}
