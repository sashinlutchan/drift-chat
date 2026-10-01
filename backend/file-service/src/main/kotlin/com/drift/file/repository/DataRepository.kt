package com.drift.file.repository

import com.drift.file.models.FileEntity
import com.drift.file.models.FileKind
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DataRepository : JpaRepository<FileEntity, UUID> {
    fun findByRoomId(roomId: UUID): List<FileEntity>

    fun findByKindAndCreatedAtBefore(
        kind: FileKind,
        before: java.time.Instant,
    ): List<FileEntity>

    fun findByOwnerIdAndKind(
        ownerId: UUID,
        kind: FileKind,
    ): List<FileEntity>
}
