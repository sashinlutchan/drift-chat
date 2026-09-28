package com.drift.user.service

import com.drift.user.common.DomainError
import com.drift.user.domain.AvatarUploader
import java.io.InputStream
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Inter-service client for file-service avatar upload (spec sections 12 and 13.1:
 * user-service streams the avatar to file-service over gRPC). Lives in the service
 * layer behind the [AvatarUploader] port. The gRPC stub is wired in Phase 2 when the
 * protos are generated; until then calls fail with a clear domain error.
 */
@Component
class FileServiceAvatarClient(
    @Value("\${FILE_SERVICE_ADDR:localhost:9084}") private val fileServiceAddr: String,
) : AvatarUploader {
    override fun uploadAvatar(
        ownerId: UUID,
        fileName: String,
        contentType: String,
        content: InputStream,
        sizeBytes: Long,
    ): UUID = notAvailable()

    override fun deleteAvatar(fileId: UUID) = notAvailable()

    private fun notAvailable(): Nothing =
        throw DomainError.ValidationFailed("Avatar storage (file-service) is not available yet — gRPC client is wired in Phase 2.")
}
