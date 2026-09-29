package com.drift.message.service

import com.drift.grpc.file.v1.AbortMultipartRequest
import com.drift.grpc.file.v1.CompleteMultipartRequest
import com.drift.grpc.file.v1.FileKind
import com.drift.grpc.file.v1.FileServiceGrpcKt
import com.drift.grpc.file.v1.PartHeader
import com.drift.grpc.file.v1.StartMultipartRequest
import com.drift.grpc.file.v1.UploadHeader
import com.drift.grpc.file.v1.UploadPartRequest
import com.drift.message.common.GrpcChannels
import com.drift.message.config.AppProperties
import com.drift.message.exceptions.DomainError
import com.drift.message.interfaces.IFileStore
import com.drift.message.models.CompletedUpload
import com.drift.message.models.StartedUpload
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * gRPC client for file-service multipart uploads (spec sections 13.6 and 16), behind [IFileStore]. A chunk is read
 * from the HTTP request in 64 KB pieces and streamed on as it arrives, so memory stays flat however big the file is.
 */
@Component
class FileStoreClient(
    properties: AppProperties,
) : IFileStore {
    private val channel: ManagedChannel = GrpcChannels.create(properties.fileServiceAddr)
    private val stub = FileServiceGrpcKt.FileServiceCoroutineStub(channel)

    override fun start(
        uploaderId: UUID,
        roomId: UUID,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
    ): StartedUpload {
        val header =
            UploadHeader
                .newBuilder()
                .setOwnerId(uploaderId.toString())
                .setRoomId(roomId.toString())
                .setKind(FileKind.FILE_KIND_MEDIA)
                .setFileName(fileName)
                .setDeclaredMimeType(mimeType)
                .build()
        val response =
            call(SHORT) {
                stub
                    .withDeadlineAfter(
                        SHORT,
                        TimeUnit.SECONDS,
                    ).startMultipart(
                        StartMultipartRequest
                            .newBuilder()
                            .setHeader(header)
                            .setSizeBytes(sizeBytes)
                            .build(),
                    )
            }
        return StartedUpload(UUID.fromString(response.fileId), response.multipartId)
    }

    override fun uploadPart(
        upload: StartedUpload,
        partNumber: Int,
        content: InputStream,
        length: Long,
    ): String {
        val header =
            PartHeader
                .newBuilder()
                .setFileId(
                    upload.fileId.toString(),
                ).setMultipartId(upload.multipartId)
                .setPartNumber(partNumber)
                .build()
        val requests =
            flow {
                emit(UploadPartRequest.newBuilder().setHeader(header).build())
                val buffer = ByteArray(PIECE_BYTES)
                var remaining = length
                while (remaining > 0) {
                    val read = content.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) throw DomainError.ValidationFailed("The upload ended before the chunk was complete.")
                    emit(UploadPartRequest.newBuilder().setChunk(ByteString.copyFrom(buffer, 0, read)).build())
                    remaining -= read
                }
            }.flowOn(Dispatchers.IO)
        return call(LONG) { stub.withDeadlineAfter(LONG, TimeUnit.SECONDS).uploadPart(requests) }.etag
    }

    override fun complete(
        upload: StartedUpload,
        sizeBytes: Long,
    ): CompletedUpload {
        val request =
            CompleteMultipartRequest
                .newBuilder()
                .setFileId(
                    upload.fileId.toString(),
                ).setMultipartId(upload.multipartId)
                .setExpectedSizeBytes(sizeBytes)
                .build()
        val response = call(LONG) { stub.withDeadlineAfter(LONG, TimeUnit.SECONDS).completeMultipart(request) }
        return CompletedUpload(UUID.fromString(response.fileId), response.mimeType, response.sizeBytes)
    }

    override fun abort(upload: StartedUpload) {
        val request =
            AbortMultipartRequest
                .newBuilder()
                .setFileId(upload.fileId.toString())
                .setMultipartId(upload.multipartId)
                .build()
        call(SHORT) { stub.withDeadlineAfter(SHORT, TimeUnit.SECONDS).abortMultipart(request) }
    }

    @PreDestroy
    fun close() {
        channel.shutdown().awaitTermination(SHORT, TimeUnit.SECONDS)
    }

    /** gRPC failures become domain errors: what file-service refused stays a 4xx, an outage becomes a 503. */
    private fun <T> call(
        @Suppress("UNUSED_PARAMETER") deadlineSeconds: Long,
        block: suspend () -> T,
    ): T =
        try {
            runBlocking { block() }
        } catch (e: StatusException) {
            throw translate(e.status)
        } catch (e: StatusRuntimeException) {
            throw translate(e.status)
        }

    private fun translate(status: Status): DomainError =
        when (status.code) {
            Status.Code.INVALID_ARGUMENT -> DomainError.ValidationFailed(status.description ?: "The file was rejected.")
            Status.Code.OUT_OF_RANGE -> DomainError.PayloadTooLarge(status.description ?: "The file is too large.")
            Status.Code.NOT_FOUND -> DomainError.NotFound(status.description ?: "File not found.")
            else -> DomainError.DependencyUnavailable("file-service is unavailable: ${status.code}")
        }

    private companion object {
        const val PIECE_BYTES = 64 * 1024
        const val SHORT = 10L
        const val LONG = 600L
    }
}
