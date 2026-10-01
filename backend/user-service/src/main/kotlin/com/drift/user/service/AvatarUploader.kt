package com.drift.user.service

import com.drift.grpc.file.v1.DeleteRequest
import com.drift.grpc.file.v1.FileKind
import com.drift.grpc.file.v1.FileServiceGrpcKt
import com.drift.grpc.file.v1.UploadHeader
import com.drift.grpc.file.v1.UploadRequest
import com.drift.user.common.GrpcChannels
import com.drift.user.exceptions.DomainError
import com.drift.user.interfaces.IAvatarUploader
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import io.micrometer.observation.ObservationRegistry
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
class AvatarUploader(
    @Value("\${FILE_SERVICE_ADDR:localhost:9084}") fileServiceAddr: String,
    observations: ObservationRegistry = ObservationRegistry.NOOP,
) : IAvatarUploader {
    private val channel: ManagedChannel = GrpcChannels.create(fileServiceAddr, observations)
    private val stub = FileServiceGrpcKt.FileServiceCoroutineStub(channel)

    override fun uploadAvatar(
        ownerId: UUID,
        fileName: String,
        contentType: String,
        content: InputStream,
        sizeBytes: Long,
    ): UUID =
        callFileService {
            val header =
                UploadHeader
                    .newBuilder()
                    .setOwnerId(ownerId.toString())
                    .setKind(FileKind.FILE_KIND_AVATAR)
                    .setFileName(fileName)
                    .setDeclaredMimeType(contentType)
                    .build()
            val requests =
                flow {
                    emit(UploadRequest.newBuilder().setHeader(header).build())
                    val buffer = ByteArray(CHUNK_BYTES)
                    while (true) {
                        val read = content.read(buffer)
                        if (read < 0) break
                        emit(UploadRequest.newBuilder().setChunk(ByteString.copyFrom(buffer, 0, read)).build())
                    }
                }
            UUID.fromString(stub.upload(requests).fileId)
        }

    override fun deleteAvatar(fileId: UUID) {
        callFileService { stub.delete(DeleteRequest.newBuilder().setFileId(fileId.toString()).build()) }
    }

    @PreDestroy
    fun close() {
        channel.shutdown().awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)
    }

    private fun <T> callFileService(block: suspend () -> T): T =
        try {
            runBlocking { block() }
        } catch (e: StatusException) {
            throw DomainError.ValidationFailed("Avatar storage failed: ${e.status.description ?: e.status.code}")
        } catch (e: StatusRuntimeException) {
            throw DomainError.ValidationFailed("Avatar storage failed: ${e.status.description ?: e.status.code}")
        }

    private companion object {
        const val CHUNK_BYTES = 64 * 1024
        const val SHUTDOWN_SECONDS = 5L
    }
}
