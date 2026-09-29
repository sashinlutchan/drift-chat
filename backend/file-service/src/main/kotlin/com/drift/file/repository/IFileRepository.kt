package com.drift.file.repository

import com.drift.file.models.FileKind
import com.drift.file.models.FileRecord
import com.drift.file.models.NewFile
import java.util.UUID

/** Repository port for file metadata (spec section 21 layering rule): the service layer never sees Spring Data. */
interface IFileRepository {
    fun save(file: NewFile): FileRecord

    fun findById(id: UUID): FileRecord?

    fun updateStored(
        id: UUID,
        mimeType: String,
        sizeBytes: Long,
    ): FileRecord?

    fun deleteById(id: UUID)

    fun findByRoom(roomId: UUID): List<FileRecord>

    /** Media records created before [before] — candidates for the abandoned-upload sweep. */
    fun findMediaOlderThan(before: java.time.Instant): List<FileRecord>

    fun findByOwnerAndKind(
        ownerId: UUID,
        kind: FileKind,
    ): List<FileRecord>
}
