package com.drift.message.interfaces

import com.drift.message.models.UploadSession
import java.time.Duration

interface IUploadSessionStore {
    fun create(
        session: UploadSession,
        ttl: Duration,
    )

    fun find(id: String): UploadSession?

    fun markChunk(
        id: String,
        index: Int,
        ttl: Duration,
    )

    fun received(id: String): Set<Int>

    fun delete(id: String)
}
