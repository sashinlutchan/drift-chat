package com.drift.file.interfaces

import com.drift.file.models.ObjectLocation
import com.drift.file.models.PartEtag
import java.io.InputStream

/** Object storage port (MinIO, spec sections 11 and 13.4). Implemented in `repository/`. */
interface IObjectStorage {
    /** Creates the buckets if missing, turns on server-side encryption. */
    fun ensureBuckets()

    fun put(
        location: ObjectLocation,
        content: ByteArray,
        contentType: String,
    )

    fun startMultipart(
        location: ObjectLocation,
        contentType: String,
    ): String

    /** Uploads one part (1-based) and returns its ETag. */
    fun uploadPart(
        location: ObjectLocation,
        multipartId: String,
        partNumber: Int,
        data: ByteArray,
    ): String

    /** Parts MinIO has received so far for this multipart upload. */
    fun listParts(
        location: ObjectLocation,
        multipartId: String,
    ): List<PartEtag>

    fun completeMultipart(
        location: ObjectLocation,
        multipartId: String,
        parts: List<PartEtag>,
    )

    fun abortMultipart(
        location: ObjectLocation,
        multipartId: String,
    )

    /** Size in bytes, or `null` when the object doesn't exist. */
    fun size(location: ObjectLocation): Long?

    /** Reads up to [length] bytes starting at [offset]. */
    fun read(
        location: ObjectLocation,
        offset: Long,
        length: Long,
    ): ByteArray

    /** Streams [length] bytes starting at [offset]; the caller closes the stream. */
    fun open(
        location: ObjectLocation,
        offset: Long,
        length: Long,
    ): InputStream

    fun delete(location: ObjectLocation)
}
