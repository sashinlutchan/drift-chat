package com.drift.file.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** JPA entity for the `files` table (spec section 18). A regular class with equality based on id (spec section 21). */
@Entity
@Table(name = "files")
class FileEntity(
    @Id
    @Column(name = "id")
    var id: UUID,
    @Column(name = "owner_id")
    var ownerId: UUID?,
    @Column(name = "room_id")
    var roomId: UUID?,
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    var kind: FileKind,
    @Column(name = "mime_type", nullable = false)
    var mimeType: String,
    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long,
    @Column(name = "object_key", nullable = false)
    var objectKey: String,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
) {
    fun toRecord(): FileRecord = FileRecord(id, ownerId, roomId, kind, mimeType, sizeBytes, objectKey, createdAt)

    override fun equals(other: Any?): Boolean = other is FileEntity && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "FileEntity(id=$id, kind=$kind)"

    companion object {
        fun from(
            file: NewFile,
            now: Instant,
        ): FileEntity = FileEntity(file.id, file.ownerId, file.roomId, file.kind, file.mimeType, file.sizeBytes, file.objectKey, now)
    }
}
