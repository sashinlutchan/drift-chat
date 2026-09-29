package com.drift.message.interfaces

import com.drift.message.models.CompletedUpload
import com.drift.message.models.StartedUpload
import java.io.InputStream
import java.util.UUID

/** file-service over gRPC (spec section 13.6). Chunks are streamed through, never held whole. */
interface IFileStore {
    fun start(
        uploaderId: UUID,
        roomId: UUID,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
    ): StartedUpload

    /** Streams [length] bytes of [content] to MinIO as multipart part [partNumber] (1-based) and returns its etag. */
    fun uploadPart(
        upload: StartedUpload,
        partNumber: Int,
        content: InputStream,
        length: Long,
    ): String

    fun complete(
        upload: StartedUpload,
        sizeBytes: Long,
    ): CompletedUpload

    fun abort(upload: StartedUpload)
}
