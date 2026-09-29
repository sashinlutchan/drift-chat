package com.drift.user.interfaces

import java.io.InputStream
import java.util.UUID

/**
 * Port for storing avatars via file-service (spec sections 12 & 13.1: the avatar is
 * streamed to file-service over gRPC). Implemented in `client/`; unit tests mock this.
 */
interface IAvatarUploader {
    /** Streams the avatar to file-service and returns the stored file id. */
    fun uploadAvatar(
        ownerId: UUID,
        fileName: String,
        contentType: String,
        content: InputStream,
        sizeBytes: Long,
    ): UUID

    fun deleteAvatar(fileId: UUID)
}
