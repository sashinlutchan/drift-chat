package com.drift.file.interfaces

import com.drift.file.models.FileContent
import com.drift.file.models.FileRecord
import com.drift.file.models.MultipartStart
import com.drift.file.models.PartEtag
import com.drift.file.models.StoredFile
import com.drift.file.models.UploadInfo
import java.util.UUID

/** File business logic (spec section 13.4); implemented by `service/FileService`. */
interface IFileService {
    /** Small single upload (avatars). */
    fun upload(
        info: UploadInfo,
        content: ByteArray,
    ): StoredFile

    fun startMultipart(
        info: UploadInfo,
        sizeBytes: Long,
    ): MultipartStart

    fun uploadPart(
        fileId: UUID,
        multipartId: String,
        partNumber: Int,
        data: ByteArray,
    ): PartEtag

    fun completeMultipart(
        fileId: UUID,
        multipartId: String,
        expectedSizeBytes: Long,
    ): StoredFile

    fun abortMultipart(
        fileId: UUID,
        multipartId: String,
    )

    fun delete(fileId: UUID): Boolean

    fun metadata(fileId: UUID): FileRecord?

    /** Opens a file for a signed-in user, enforcing room membership for media, honouring an HTTP `Range` header. */
    fun open(
        fileId: UUID,
        userId: UUID,
        rangeHeader: String?,
    ): FileContent

    /** Removes records of uploads that were started but never finished (no object in storage) and are older than [olderThan]. */
    fun sweepAbandonedUploads(olderThan: java.time.Duration): Int

    fun deleteByRoom(roomId: UUID): Int

    fun deleteAvatarsOwnedBy(userId: UUID): Int
}
