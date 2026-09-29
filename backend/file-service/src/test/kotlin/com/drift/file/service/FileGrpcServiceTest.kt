package com.drift.file.service

import com.drift.file.config.AppProperties
import com.drift.file.interfaces.IFileService
import com.drift.file.models.FileKind
import com.drift.file.models.FileRecord
import com.drift.file.models.MultipartStart
import com.drift.file.models.PartEtag
import com.drift.file.models.StoredFile
import com.drift.file.models.UploadInfo
import com.drift.grpc.file.v1.AbortMultipartRequest
import com.drift.grpc.file.v1.CompleteMultipartRequest
import com.drift.grpc.file.v1.DeleteRequest
import com.drift.grpc.file.v1.GetMetadataRequest
import com.drift.grpc.file.v1.PartHeader
import com.drift.grpc.file.v1.StartMultipartRequest
import com.drift.grpc.file.v1.UploadHeader
import com.drift.grpc.file.v1.UploadPartRequest
import com.drift.grpc.file.v1.UploadRequest
import com.google.protobuf.ByteString
import io.grpc.Status
import io.grpc.StatusException
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.util.unit.DataSize
import java.time.Instant
import java.util.UUID
import com.drift.grpc.file.v1.FileKind as ProtoKind

class FileGrpcServiceTest {
    private val files = mockk<IFileService>()
    private val properties =
        AppProperties(
            jwtSecret = "x".repeat(32),
            sessionCookieName = "c",
            maxAvatarSize = DataSize.ofBytes(100),
            roomServiceAddr = "r",
            storage =
                AppProperties.Storage(
                    "http://m",
                    "a",
                    "b",
                    "av",
                    "me",
                    DataSize.ofMegabytes(1),
                    java.time.Duration.ofDays(2),
                    listOf("image/png"),
                ),
        )
    private val grpc = FileGrpcService(files, properties)
    private val owner = UUID.randomUUID()
    private val room = UUID.randomUUID()
    private val fileId = UUID.randomUUID()

    private fun header(
        kind: ProtoKind,
        roomId: UUID? = null,
    ) = UploadHeader
        .newBuilder()
        .setOwnerId(owner.toString())
        .setRoomId(roomId?.toString().orEmpty())
        .setKind(kind)
        .setFileName("f")
        .setDeclaredMimeType("image/png")
        .build()

    private fun chunk(n: Int) = UploadRequest.newBuilder().setChunk(ByteString.copyFrom(ByteArray(n))).build()

    private fun code(block: () -> Unit) =
        assertThatThrownBy(block).isInstanceOf(StatusException::class.java).extracting {
            (it as StatusException).status.code
        }

    @Test
    fun `upload gathers the chunks after the header and stores them`() =
        runBlocking {
            val info = slot<UploadInfo>()
            val content = slot<ByteArray>()
            every { files.upload(capture(info), capture(content)) } returns StoredFile(fileId, "image/png", 30)

            val response =
                grpc.upload(
                    flowOf(UploadRequest.newBuilder().setHeader(header(ProtoKind.FILE_KIND_AVATAR)).build(), chunk(10), chunk(20)),
                )

            assertThat(response.fileId).isEqualTo(fileId.toString())
            assertThat(content.captured).hasSize(30)
            assertThat(info.captured.kind).isEqualTo(FileKind.AVATAR)
            assertThat(info.captured.ownerId).isEqualTo(owner)
            assertThat(info.captured.roomId).isNull()
        }

    @Test
    fun `upload without a header or with chunks first is invalid`() {
        code { runBlocking { grpc.upload(flowOf(chunk(5))) } }.isEqualTo(Status.Code.INVALID_ARGUMENT)
        code { runBlocking { grpc.upload(flowOf()) } }.isEqualTo(Status.Code.INVALID_ARGUMENT)
    }

    @Test
    fun `upload stops as soon as the avatar limit is exceeded`() {
        code {
            runBlocking {
                grpc.upload(flowOf(UploadRequest.newBuilder().setHeader(header(ProtoKind.FILE_KIND_AVATAR)).build(), chunk(60), chunk(60)))
            }
        }.isEqualTo(Status.Code.OUT_OF_RANGE)
    }

    @Test
    fun `an unspecified kind is rejected`() {
        code {
            runBlocking { grpc.upload(flowOf(UploadRequest.newBuilder().setHeader(header(ProtoKind.FILE_KIND_UNSPECIFIED)).build())) }
        }.isEqualTo(Status.Code.INVALID_ARGUMENT)
    }

    @Test
    fun `start multipart returns the ids`() =
        runBlocking {
            every { files.startMultipart(any(), 500) } returns MultipartStart(fileId, "mp-1")

            val response =
                grpc.startMultipart(
                    StartMultipartRequest
                        .newBuilder()
                        .setHeader(header(ProtoKind.FILE_KIND_MEDIA, room))
                        .setSizeBytes(500)
                        .build(),
                )

            assertThat(response.fileId).isEqualTo(fileId.toString())
            assertThat(response.multipartId).isEqualTo("mp-1")
        }

    @Test
    fun `upload part reads the header then the bytes and returns the etag`() =
        runBlocking {
            every { files.uploadPart(fileId, "mp-1", 2, any()) } returns PartEtag(2, "etag")
            val partHeader =
                PartHeader
                    .newBuilder()
                    .setFileId(fileId.toString())
                    .setMultipartId("mp-1")
                    .setPartNumber(2)
                    .build()

            val response =
                grpc.uploadPart(
                    flowOf(
                        UploadPartRequest.newBuilder().setHeader(partHeader).build(),
                        UploadPartRequest.newBuilder().setChunk(ByteString.copyFrom(ByteArray(7))).build(),
                        UploadPartRequest.newBuilder().setChunk(ByteString.copyFrom(ByteArray(3))).build(),
                    ),
                )

            assertThat(response.etag).isEqualTo("etag")
            assertThat(response.sizeBytes).isEqualTo(10)
            verify { files.uploadPart(fileId, "mp-1", 2, match { it.size == 10 }) }
        }

    @Test
    fun `upload part needs its header first`() {
        code {
            runBlocking { grpc.uploadPart(flowOf(UploadPartRequest.newBuilder().setChunk(ByteString.copyFrom(ByteArray(1))).build())) }
        }.isEqualTo(Status.Code.INVALID_ARGUMENT)
        code { runBlocking { grpc.uploadPart(flowOf()) } }.isEqualTo(Status.Code.INVALID_ARGUMENT)
    }

    @Test
    fun `complete abort and delete delegate`() =
        runBlocking {
            every { files.completeMultipart(fileId, "mp-1", 9) } returns StoredFile(fileId, "video/mp4", 9)
            every { files.abortMultipart(fileId, "mp-1") } just runs
            every { files.delete(fileId) } returns true

            val done =
                grpc.completeMultipart(
                    CompleteMultipartRequest
                        .newBuilder()
                        .setFileId(fileId.toString())
                        .setMultipartId("mp-1")
                        .setExpectedSizeBytes(9)
                        .build(),
                )
            grpc.abortMultipart(
                AbortMultipartRequest
                    .newBuilder()
                    .setFileId(fileId.toString())
                    .setMultipartId("mp-1")
                    .build(),
            )
            val deleted = grpc.delete(DeleteRequest.newBuilder().setFileId(fileId.toString()).build())

            assertThat(done.mimeType).isEqualTo("video/mp4")
            assertThat(deleted.deleted).isTrue()
            verify { files.abortMultipart(fileId, "mp-1") }
        }

    @Test
    fun `metadata reports found and not found`() =
        runBlocking {
            val rec = FileRecord(fileId, owner, room, FileKind.MEDIA, "video/mp4", 5, "k", Instant.EPOCH)
            every { files.metadata(fileId) } returns rec
            every { files.metadata(neq(fileId)) } returns null

            val found = grpc.getMetadata(GetMetadataRequest.newBuilder().setFileId(fileId.toString()).build())
            val missing = grpc.getMetadata(GetMetadataRequest.newBuilder().setFileId(UUID.randomUUID().toString()).build())

            assertThat(found.found).isTrue()
            assertThat(found.roomId).isEqualTo(room.toString())
            assertThat(found.kind).isEqualTo(ProtoKind.FILE_KIND_MEDIA)
            assertThat(missing.found).isFalse()
        }

    @Test
    fun `bad ids and domain errors become gRPC statuses, unexpected ones are hidden`() {
        code { runBlocking { grpc.delete(DeleteRequest.newBuilder().setFileId("nope").build()) } }.isEqualTo(Status.Code.INVALID_ARGUMENT)

        every { files.delete(fileId) } throws
            com.drift.file.exceptions.DomainError
                .NotFound("gone")
        code {
            runBlocking {
                grpc.delete(
                    DeleteRequest.newBuilder().setFileId(fileId.toString()).build(),
                )
            }
        }.isEqualTo(Status.Code.NOT_FOUND)

        every { files.delete(fileId) } throws IllegalStateException("secret internals")
        assertThatThrownBy { runBlocking { grpc.delete(DeleteRequest.newBuilder().setFileId(fileId.toString()).build()) } }
            .isInstanceOf(StatusException::class.java)
            .hasMessageNotContaining("secret")
    }
}
