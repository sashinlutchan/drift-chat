package com.drift.user.interfaces

import java.io.InputStream
import java.util.UUID

interface IAvatarUploader {
    fun uploadAvatar(
        ownerId: UUID,
        fileName: String,
        contentType: String,
        content: InputStream,
        sizeBytes: Long,
    ): UUID

    fun deleteAvatar(fileId: UUID)
}
