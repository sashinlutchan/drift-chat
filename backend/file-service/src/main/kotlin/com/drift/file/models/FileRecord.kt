package com.drift.file.models

import java.time.Instant
import java.util.UUID

data class FileRecord(
    val id: UUID,
    val ownerId: UUID?,
    val roomId: UUID?,
    val kind: FileKind,
    val mimeType: String,
    val sizeBytes: Long,
    val objectKey: String,
    val createdAt: Instant,
)

data class NewFile(
    val id: UUID,
    val ownerId: UUID?,
    val roomId: UUID?,
    val kind: FileKind,
    val mimeType: String,
    val sizeBytes: Long,
    val objectKey: String,
)

data class ObjectLocation(
    val bucket: String,
    val key: String,
)

data class PartEtag(
    val partNumber: Int,
    val etag: String,
)

data class UploadInfo(
    val ownerId: UUID?,
    val roomId: UUID?,
    val kind: FileKind,
    val fileName: String,
    val declaredMimeType: String,
)

data class StoredFile(
    val id: UUID,
    val mimeType: String,
    val sizeBytes: Long,
)

data class MultipartStart(
    val fileId: UUID,
    val multipartId: String,
)

data class ByteRange(
    val start: Long,
    val end: Long,
) {
    val length: Long get() = end - start + 1
}

data class FileContent(
    val record: FileRecord,
    val range: ByteRange,
    val partial: Boolean,
    val open: () -> java.io.InputStream,
)
