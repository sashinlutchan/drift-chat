package com.drift.user.service

import com.drift.grpc.file.v1.DeleteRequest
import com.drift.grpc.file.v1.DeleteResponse
import com.drift.grpc.file.v1.FileKind
import com.drift.grpc.file.v1.FileServiceGrpcKt
import com.drift.grpc.file.v1.UploadRequest
import com.drift.grpc.file.v1.UploadResponse
import com.drift.user.exceptions.DomainError
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.netty.NettyServerBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.util.UUID

class AvatarUploaderTest {
    private val received = mutableListOf<UploadRequest>()
    private val deleted = mutableListOf<String>()
    private var failUpload = false
    private val fileId = UUID.randomUUID()

    private val fakeFileService =
        object : FileServiceGrpcKt.FileServiceCoroutineImplBase() {
            override suspend fun upload(requests: Flow<UploadRequest>): UploadResponse {
                received += requests.toList()
                if (failUpload) throw StatusException(Status.INVALID_ARGUMENT.withDescription("bad type"))
                return UploadResponse.newBuilder().setFileId(fileId.toString()).build()
            }

            override suspend fun delete(request: DeleteRequest): DeleteResponse {
                deleted += request.fileId
                return DeleteResponse.newBuilder().setDeleted(true).build()
            }
        }

    private val server: Server =
        NettyServerBuilder
            .forAddress(
                InetSocketAddress("localhost", 0),
            ).addService(fakeFileService)
            .build()
            .start()
    private val uploader = AvatarUploader("localhost:${server.port}")

    @AfterEach
    fun stop() {
        uploader.close()
        server.shutdownNow()
    }

    @Test
    fun `streams a header then the avatar bytes and returns the file id`() {
        val bytes = ByteArray(150_000) { it.toByte() }
        val owner = UUID.randomUUID()

        val result = uploader.uploadAvatar(owner, "a.png", "image/png", ByteArrayInputStream(bytes), bytes.size.toLong())

        assertThat(result).isEqualTo(fileId)
        val header = received.first().header
        assertThat(header.ownerId).isEqualTo(owner.toString())
        assertThat(header.kind).isEqualTo(FileKind.FILE_KIND_AVATAR)
        assertThat(header.declaredMimeType).isEqualTo("image/png")
        assertThat(received.drop(1).sumOf { it.chunk.size() }).isEqualTo(bytes.size)
        assertThat(received.size).isGreaterThan(2)
    }

    @Test
    fun `file service rejection becomes a validation error`() {
        failUpload = true
        assertThatThrownBy { uploader.uploadAvatar(UUID.randomUUID(), "a.png", "image/png", ByteArrayInputStream(byteArrayOf(1)), 1) }
            .isInstanceOf(DomainError.ValidationFailed::class.java)
            .hasMessageContaining("bad type")
    }

    @Test
    fun `delete forwards the file id`() {
        uploader.deleteAvatar(fileId)
        assertThat(deleted).containsExactly(fileId.toString())
    }
}
