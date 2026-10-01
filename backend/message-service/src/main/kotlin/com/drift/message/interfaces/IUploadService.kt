package com.drift.message.interfaces

import com.drift.message.models.Message
import com.drift.message.models.UploadSession
import java.io.InputStream
import java.util.UUID

interface IUploadService {
    fun start(
        userId: UUID,
        roomId: UUID,
        fileName: String,
        sizeBytes: Long,
        mimeType: String,
    ): UploadSession

    fun uploadChunk(
        userId: UUID,
        uploadId: String,
        index: Int,
        length: Long,
        content: InputStream,
    )

    fun status(
        userId: UUID,
        uploadId: String,
    ): Pair<UploadSession, Set<Int>>

    fun complete(
        userId: UUID,
        uploadId: String,
    ): Message

    fun cancel(
        userId: UUID,
        uploadId: String,
    )
}
