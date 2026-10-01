package com.drift.file.repository

import com.drift.file.models.FileEntity
import com.drift.file.models.FileKind
import com.drift.file.models.FileRecord
import com.drift.file.models.NewFile
import org.springframework.stereotype.Repository
import java.time.Clock
import java.util.UUID

@Repository
class FileRepository(
    private val data: DataRepository,
    private val clock: Clock,
) : IFileRepository {
    override fun save(file: NewFile): FileRecord = data.save(FileEntity.from(file, clock.instant())).toRecord()

    override fun findById(id: UUID): FileRecord? = data.findById(id).map(FileEntity::toRecord).orElse(null)

    override fun updateStored(
        id: UUID,
        mimeType: String,
        sizeBytes: Long,
    ): FileRecord? {
        val entity = data.findById(id).orElse(null) ?: return null
        entity.mimeType = mimeType
        entity.sizeBytes = sizeBytes
        return data.save(entity).toRecord()
    }

    override fun deleteById(id: UUID) = data.deleteById(id)

    override fun findByRoom(roomId: UUID): List<FileRecord> = data.findByRoomId(roomId).map(FileEntity::toRecord)

    override fun findMediaOlderThan(before: java.time.Instant): List<FileRecord> =
        data.findByKindAndCreatedAtBefore(FileKind.MEDIA, before).map(FileEntity::toRecord)

    override fun findByOwnerAndKind(
        ownerId: UUID,
        kind: FileKind,
    ): List<FileRecord> = data.findByOwnerIdAndKind(ownerId, kind).map(FileEntity::toRecord)
}
