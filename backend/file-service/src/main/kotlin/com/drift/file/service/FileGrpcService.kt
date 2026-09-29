package com.drift.file.service

import com.drift.file.config.AppProperties
import com.drift.file.exceptions.DomainError
import com.drift.file.exceptions.grpcGuard
import com.drift.file.interfaces.IFileService
import com.drift.file.models.FileKind
import com.drift.file.models.UploadInfo
import com.drift.grpc.file.v1.AbortMultipartRequest
import com.drift.grpc.file.v1.AbortMultipartResponse
import com.drift.grpc.file.v1.CompleteMultipartRequest
import com.drift.grpc.file.v1.CompleteMultipartResponse
import com.drift.grpc.file.v1.DeleteRequest
import com.drift.grpc.file.v1.DeleteResponse
import com.drift.grpc.file.v1.FileServiceGrpcKt
import com.drift.grpc.file.v1.GetMetadataRequest
import com.drift.grpc.file.v1.GetMetadataResponse
import com.drift.grpc.file.v1.PartHeader
import com.drift.grpc.file.v1.StartMultipartRequest
import com.drift.grpc.file.v1.StartMultipartResponse
import com.drift.grpc.file.v1.UploadHeader
import com.drift.grpc.file.v1.UploadPartRequest
import com.drift.grpc.file.v1.UploadPartResponse
import com.drift.grpc.file.v1.UploadRequest
import com.drift.grpc.file.v1.UploadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.util.UUID
import com.drift.grpc.file.v1.FileKind as ProtoKind

/**
 * gRPC server for `file.v1.FileService` (spec section 16). Client streams are drained chunk by chunk into a
 * bounded buffer (one avatar, or one part of a large file) — never the whole 1 GB file.
 */
@Service
class FileGrpcService(
    private val files: IFileService,
    private val properties: AppProperties,
) : FileServiceGrpcKt.FileServiceCoroutineImplBase() {
    override suspend fun upload(requests: Flow<UploadRequest>): UploadResponse =
        grpcGuard {
            var info: UploadInfo? = null
            var limit = 0L
            val buffer = ByteArrayOutputStream()
            // The stream is collected once, checking the size limit as bytes arrive so an oversized upload is cut off early.
            requests.collect { message ->
                if (message.hasHeader()) {
                    info = message.header.toInfo()
                    limit =
                        if (info?.kind == FileKind.AVATAR) properties.maxAvatarSize.toBytes() else properties.storage.maxFileSize.toBytes()
                } else if (message.hasChunk()) {
                    if (info == null) throw DomainError.ValidationFailed("The first message must carry the upload header.")
                    if (buffer.size() + message.chunk.size() >
                        limit
                    ) {
                        throw DomainError.PayloadTooLarge("The file is larger than the allowed $limit bytes.")
                    }
                    message.chunk.writeTo(buffer)
                }
            }
            val header = info ?: throw DomainError.ValidationFailed("The first message must carry the upload header.")
            val stored = withContext(Dispatchers.IO) { files.upload(header, buffer.toByteArray()) }
            UploadResponse
                .newBuilder()
                .setFileId(stored.id.toString())
                .setMimeType(stored.mimeType)
                .setSizeBytes(stored.sizeBytes)
                .build()
        }

    override suspend fun startMultipart(request: StartMultipartRequest): StartMultipartResponse =
        grpcGuard {
            val started = withContext(Dispatchers.IO) { files.startMultipart(request.header.toInfo(), request.sizeBytes) }
            StartMultipartResponse
                .newBuilder()
                .setFileId(started.fileId.toString())
                .setMultipartId(started.multipartId)
                .build()
        }

    override suspend fun uploadPart(requests: Flow<UploadPartRequest>): UploadPartResponse =
        grpcGuard {
            var header: PartHeader? = null
            val buffer = ByteArrayOutputStream()
            requests.collect { message ->
                if (message.hasHeader()) {
                    header = message.header
                } else if (message.hasChunk()) {
                    if (header == null) throw DomainError.ValidationFailed("The first message must carry the part header.")
                    if (buffer.size() + message.chunk.size() >
                        MAX_PART_BYTES
                    ) {
                        throw DomainError.PayloadTooLarge("A part can be at most $MAX_PART_BYTES bytes.")
                    }
                    message.chunk.writeTo(buffer)
                }
            }
            val partHeader = header ?: throw DomainError.ValidationFailed("The first message must carry the part header.")
            val part =
                withContext(Dispatchers.IO) {
                    files.uploadPart(parseId(partHeader.fileId), partHeader.multipartId, partHeader.partNumber, buffer.toByteArray())
                }
            UploadPartResponse
                .newBuilder()
                .setPartNumber(part.partNumber)
                .setEtag(part.etag)
                .setSizeBytes(buffer.size().toLong())
                .build()
        }

    override suspend fun completeMultipart(request: CompleteMultipartRequest): CompleteMultipartResponse =
        grpcGuard {
            val stored =
                withContext(Dispatchers.IO) {
                    files.completeMultipart(parseId(request.fileId), request.multipartId, request.expectedSizeBytes)
                }
            CompleteMultipartResponse
                .newBuilder()
                .setFileId(
                    stored.id.toString(),
                ).setMimeType(stored.mimeType)
                .setSizeBytes(stored.sizeBytes)
                .build()
        }

    override suspend fun abortMultipart(request: AbortMultipartRequest): AbortMultipartResponse =
        grpcGuard {
            withContext(Dispatchers.IO) { files.abortMultipart(parseId(request.fileId), request.multipartId) }
            AbortMultipartResponse.getDefaultInstance()
        }

    override suspend fun delete(request: DeleteRequest): DeleteResponse =
        grpcGuard {
            val deleted = withContext(Dispatchers.IO) { files.delete(parseId(request.fileId)) }
            DeleteResponse.newBuilder().setDeleted(deleted).build()
        }

    override suspend fun getMetadata(request: GetMetadataRequest): GetMetadataResponse =
        grpcGuard {
            val record = withContext(Dispatchers.IO) { files.metadata(parseId(request.fileId)) }
            if (record == null) {
                GetMetadataResponse.newBuilder().setFound(false).build()
            } else {
                GetMetadataResponse
                    .newBuilder()
                    .setFound(true)
                    .setFileId(record.id.toString())
                    .setOwnerId(record.ownerId?.toString().orEmpty())
                    .setRoomId(record.roomId?.toString().orEmpty())
                    .setKind(if (record.kind == FileKind.AVATAR) ProtoKind.FILE_KIND_AVATAR else ProtoKind.FILE_KIND_MEDIA)
                    .setMimeType(record.mimeType)
                    .setSizeBytes(record.sizeBytes)
                    .build()
            }
        }

    private fun UploadHeader.toInfo(): UploadInfo =
        UploadInfo(
            ownerId = ownerId.takeIf { it.isNotBlank() }?.let(::parseId),
            roomId = roomId.takeIf { it.isNotBlank() }?.let(::parseId),
            kind =
                when (kind) {
                    ProtoKind.FILE_KIND_AVATAR -> FileKind.AVATAR
                    ProtoKind.FILE_KIND_MEDIA -> FileKind.MEDIA
                    else -> throw DomainError.ValidationFailed("The file kind is required.")
                },
            fileName = fileName,
            declaredMimeType = declaredMimeType,
        )

    private companion object {
        const val MAX_PART_BYTES = 64 * 1024 * 1024
    }

    private fun parseId(value: String): UUID =
        runCatching { UUID.fromString(value) }.getOrElse { throw DomainError.ValidationFailed("Invalid id: $value") }
}
