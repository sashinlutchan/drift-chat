package com.drift.file.models

import java.time.Instant
import java.util.UUID

/** Domain model for a stored file's metadata — no Spring/JPA annotations (spec section 21). */
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

/** Data needed to create a file record. */
data class NewFile(
    val id: UUID,
    val ownerId: UUID?,
    val roomId: UUID?,
    val kind: FileKind,
    val mimeType: String,
    val sizeBytes: Long,
    val objectKey: String,
)

/** Where a file lives in object storage. */
data class ObjectLocation(
    val bucket: String,
    val key: String,
)

/** A finished multipart part. */
data class PartEtag(
    val partNumber: Int,
    val etag: String,
)

/** Header of an upload request, as received over gRPC. */
data class UploadInfo(
    val ownerId: UUID?,
    val roomId: UUID?,
    val kind: FileKind,
    val fileName: String,
    val declaredMimeType: String,
)

/** Outcome of a completed upload. */
data class StoredFile(
    val id: UUID,
    val mimeType: String,
    val sizeBytes: Long,
)

/** A multipart upload that has been started. */
data class MultipartStart(
    val fileId: UUID,
    val multipartId: String,
)

/** A byte range of a file, inclusive on both ends, already validated against the file size. */
data class ByteRange(
    val start: Long,
    val end: Long,
) {
    val length: Long get() = end - start + 1
}

/** Everything the REST layer needs to stream a file. */
data class FileContent(
    val record: FileRecord,
    val range: ByteRange,
    val partial: Boolean,
    val open: () -> java.io.InputStream,
)
