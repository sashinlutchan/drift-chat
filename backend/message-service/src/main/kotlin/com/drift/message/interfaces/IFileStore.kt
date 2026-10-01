package com.drift.message.interfaces

import com.drift.message.models.CompletedUpload
import com.drift.message.models.StartedUpload
import java.io.InputStream
import java.util.UUID

interface IFileStore {
    fun start(
        uploaderId: UUID,
        roomId: UUID,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
    ): StartedUpload

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
