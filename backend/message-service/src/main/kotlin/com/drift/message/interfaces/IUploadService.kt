package com.drift.message.interfaces

import com.drift.message.models.Message
import com.drift.message.models.UploadSession
import java.io.InputStream
import java.util.UUID

/** Chunked, resumable uploads of up to 1 GB (spec section 13.6); implemented by `service/UploadService`. */
interface IUploadService {
    fun start(
        userId: UUID,
        roomId: UUID,
        fileName: String,
        sizeBytes: Long,
        mimeType: String,
    ): UploadSession

    /** Streams one chunk straight to file-service. */
    fun uploadChunk(
        userId: UUID,
        uploadId: String,
        index: Int,
        length: Long,
        content: InputStream,
    )

    /** The session and which chunks have arrived, so a client can resume. */
    fun status(
        userId: UUID,
        uploadId: String,
    ): Pair<UploadSession, Set<Int>>

    /** Finishes the upload, creates and broadcasts the media message. */
    fun complete(
        userId: UUID,
        uploadId: String,
    ): Message

    fun cancel(
        userId: UUID,
        uploadId: String,
    )
}
