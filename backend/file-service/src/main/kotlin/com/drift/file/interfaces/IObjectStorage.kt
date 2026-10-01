package com.drift.file.interfaces

import com.drift.file.models.ObjectLocation
import com.drift.file.models.PartEtag
import java.io.InputStream

interface IObjectStorage {
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

    fun uploadPart(
        location: ObjectLocation,
        multipartId: String,
        partNumber: Int,
        data: ByteArray,
    ): String

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

    fun size(location: ObjectLocation): Long?

    fun read(
        location: ObjectLocation,
        offset: Long,
        length: Long,
    ): ByteArray

    fun open(
        location: ObjectLocation,
        offset: Long,
        length: Long,
    ): InputStream

    fun delete(location: ObjectLocation)
}
