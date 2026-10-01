package com.drift.message.service

import com.drift.grpc.file.v1.AbortMultipartRequest
import com.drift.grpc.file.v1.AbortMultipartResponse
import com.drift.grpc.file.v1.CompleteMultipartRequest
import com.drift.grpc.file.v1.CompleteMultipartResponse
import com.drift.grpc.file.v1.FileKind
import com.drift.grpc.file.v1.FileServiceGrpcKt
import com.drift.grpc.file.v1.StartMultipartRequest
import com.drift.grpc.file.v1.StartMultipartResponse
import com.drift.grpc.file.v1.UploadPartRequest
import com.drift.grpc.file.v1.UploadPartResponse
import com.drift.grpc.room.v1.IsMemberRequest
import com.drift.grpc.room.v1.IsMemberResponse
import com.drift.grpc.room.v1.RoomServiceGrpcKt
import com.drift.grpc.user.v1.GetUsersRequest
import com.drift.grpc.user.v1.GetUsersResponse
import com.drift.grpc.user.v1.SetPresenceRequest
import com.drift.grpc.user.v1.SetPresenceResponse
import com.drift.grpc.user.v1.UserServiceGrpcKt
import com.drift.message.TestSupport
import com.drift.message.config.AppProperties
import com.drift.message.exceptions.DomainError
import com.drift.message.models.RoomAccess
import com.drift.message.models.StartedUpload
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
import com.drift.grpc.user.v1.UserProfile as ProtoProfile

class GrpcClientsTest {
    private val servers = mutableListOf<Server>()
    private val closers = mutableListOf<() -> Unit>()

    @AfterEach
    fun stop() {
        closers.forEach { it() }
        servers.forEach { it.shutdownNow() }
    }

    private fun serve(service: io.grpc.BindableService): Int =
        NettyServerBuilder
            .forAddress(InetSocketAddress("localhost", 0))
            .addService(service)
            .build()
            .start()
            .also(servers::add)
            .port

    private fun props(
        user: Int = 1,
        room: Int = 1,
        file: Int = 1,
    ): AppProperties =
        TestSupport.properties().copy(
            userServiceAddr = "localhost:$user",
            roomServiceAddr = "localhost:$room",
            fileServiceAddr = "localhost:$file",
        )

    @Test
    fun `room access maps member, non member and missing room`() {
        val member = UUID.randomUUID()
        val existing = UUID.randomUUID()
        val port =
            serve(
                object : RoomServiceGrpcKt.RoomServiceCoroutineImplBase() {
                    override suspend fun isMember(request: IsMemberRequest): IsMemberResponse {
                        val exists = request.roomId == existing.toString()
                        return IsMemberResponse
                            .newBuilder()
                            .setRoomExists(
                                exists,
                            ).setIsMember(exists && request.userId == member.toString())
                            .build()
                    }
                },
            )
        val client = RoomMembershipClient(props(room = port)).also { closers += it::close }

        assertThat(client.access(existing, member)).isEqualTo(RoomAccess.MEMBER)
        assertThat(client.access(existing, UUID.randomUUID())).isEqualTo(RoomAccess.NOT_MEMBER)
        assertThat(client.access(UUID.randomUUID(), member)).isEqualTo(RoomAccess.NO_SUCH_ROOM)
    }

    @Test
    fun `room-service being down is a dependency error, not a wrong answer`() {
        val client = RoomMembershipClient(props(room = 1)).also { closers += it::close }

        assertThatThrownBy {
            client.access(
                UUID.randomUUID(),
                UUID.randomUUID(),
            )
        }.isInstanceOf(DomainError.DependencyUnavailable::class.java)
    }

    @Test
    fun `profiles are looked up in one call and presence is reported`() {
        val sipho = UUID.randomUUID()
        val presence = mutableListOf<Pair<String, Boolean>>()
        val port =
            serve(
                object : UserServiceGrpcKt.UserServiceCoroutineImplBase() {
                    override suspend fun getUsers(request: GetUsersRequest): GetUsersResponse =
                        GetUsersResponse
                            .newBuilder()
                            .addAllUsers(
                                request.userIdsList.filter { it == sipho.toString() }.map {
                                    ProtoProfile
                                        .newBuilder()
                                        .setId(
                                            it,
                                        ).setUsername("sipho")
                                        .setName("Sipho")
                                        .setAvatarFileId(UUID(0, 9).toString())
                                        .setOnline(true)
                                        .build()
                                },
                            ).build()

                    override suspend fun setPresence(request: SetPresenceRequest): SetPresenceResponse {
                        presence += request.userId to request.online
                        return SetPresenceResponse.getDefaultInstance()
                    }
                },
            )
        val client = UserDirectoryClient(props(user = port)).also { closers += it::close }

        val profiles = client.profiles(listOf(sipho, UUID.randomUUID(), sipho))
        client.setPresence(sipho, true)

        assertThat(profiles).containsOnlyKeys(sipho)
        assertThat(profiles.getValue(sipho).avatarFileId).isEqualTo(UUID(0, 9))
        assertThat(presence).containsExactly(sipho.toString() to true)
        assertThat(client.profiles(emptyList())).isEmpty()
    }

    @Test
    fun `user-service being down is a dependency error`() {
        val client = UserDirectoryClient(props(user = 1)).also { closers += it::close }

        assertThatThrownBy { client.profiles(listOf(UUID.randomUUID())) }.isInstanceOf(DomainError.DependencyUnavailable::class.java)
        assertThatThrownBy { client.setPresence(UUID.randomUUID(), true) }.isInstanceOf(DomainError.DependencyUnavailable::class.java)
    }

    private class FakeFiles : FileServiceGrpcKt.FileServiceCoroutineImplBase() {
        var rejectWith: Status? = null
        val startedKinds = mutableListOf<FileKind>()
        val partSizes = mutableMapOf<Int, Int>()
        var partMessages = 0
        val completes = mutableListOf<Long>()
        val aborted = mutableListOf<String>()

        override suspend fun startMultipart(request: StartMultipartRequest): StartMultipartResponse {
            startedKinds += request.header.kind
            return StartMultipartResponse
                .newBuilder()
                .setFileId(UUID(0, 1).toString())
                .setMultipartId("mp-1")
                .build()
        }

        override suspend fun uploadPart(requests: Flow<UploadPartRequest>): UploadPartResponse {
            val all = requests.toList()
            rejectWith?.let { throw StatusException(it) }
            val header = all.first().header
            partMessages = all.size
            partSizes[header.partNumber] = all.drop(1).sumOf { it.chunk.size() }
            return UploadPartResponse
                .newBuilder()
                .setPartNumber(header.partNumber)
                .setEtag("etag-${header.partNumber}")
                .build()
        }

        override suspend fun completeMultipart(request: CompleteMultipartRequest): CompleteMultipartResponse {
            rejectWith?.let { throw StatusException(it) }
            completes += request.expectedSizeBytes
            return CompleteMultipartResponse
                .newBuilder()
                .setFileId(
                    request.fileId,
                ).setMimeType("video/mp4")
                .setSizeBytes(request.expectedSizeBytes)
                .build()
        }

        override suspend fun abortMultipart(request: AbortMultipartRequest): AbortMultipartResponse {
            aborted += request.multipartId
            return AbortMultipartResponse.getDefaultInstance()
        }
    }

    @Test
    fun `a chunk is streamed in small pieces and the whole chunk arrives`() {
        val fake = FakeFiles()
        val client = FileStoreClient(props(file = serve(fake))).also { closers += it::close }
        val upload = client.start(UUID.randomUUID(), UUID.randomUUID(), "clip.mp4", "video/mp4", 500_000)
        val data = ByteArray(300_000) { it.toByte() }

        val etag = client.uploadPart(upload, 3, ByteArrayInputStream(data), data.size.toLong())

        assertThat(etag).isEqualTo("etag-3")
        assertThat(fake.startedKinds).containsExactly(FileKind.FILE_KIND_MEDIA)
        assertThat(fake.partSizes[3]).isEqualTo(300_000)
        assertThat(fake.partMessages).isGreaterThan(4)
    }

    @Test
    fun `complete and abort reach file-service`() {
        val fake = FakeFiles()
        val client = FileStoreClient(props(file = serve(fake))).also { closers += it::close }
        val upload = StartedUpload(UUID(0, 1), "mp-1")

        val done = client.complete(upload, 123)
        client.abort(upload)

        assertThat(done.mimeType).isEqualTo("video/mp4")
        assertThat(done.sizeBytes).isEqualTo(123)
        assertThat(fake.aborted).containsExactly("mp-1")
    }

    @Test
    fun `what file-service refuses stays a client error and an outage becomes a 503`() {
        val fake = FakeFiles()
        val client = FileStoreClient(props(file = serve(fake))).also { closers += it::close }
        val upload = StartedUpload(UUID(0, 1), "mp-1")

        fake.rejectWith = Status.INVALID_ARGUMENT.withDescription("wrong type")
        assertThatThrownBy {
            client.complete(
                upload,
                1,
            )
        }.isInstanceOf(DomainError.ValidationFailed::class.java).hasMessageContaining("wrong type")

        fake.rejectWith = Status.FAILED_PRECONDITION.withDescription("Files of type image/svg+xml are not supported.")
        assertThatThrownBy { client.complete(upload, 1) }
            .isInstanceOf(DomainError.UnsupportedMediaType::class.java)
            .hasMessageContaining("svg")

        fake.rejectWith = Status.OUT_OF_RANGE.withDescription("too big")
        assertThatThrownBy { client.complete(upload, 1) }.isInstanceOf(DomainError.PayloadTooLarge::class.java)

        fake.rejectWith = Status.NOT_FOUND.withDescription("gone")
        assertThatThrownBy { client.complete(upload, 1) }.isInstanceOf(DomainError.NotFound::class.java)

        val down = FileStoreClient(props(file = 1)).also { closers += it::close }
        assertThatThrownBy { down.abort(upload) }.isInstanceOf(DomainError.DependencyUnavailable::class.java)
    }

    @Test
    fun `a request body that ends early is reported instead of hanging`() {
        val client = FileStoreClient(props(file = serve(FakeFiles()))).also { closers += it::close }

        assertThatThrownBy { client.uploadPart(StartedUpload(UUID(0, 1), "mp-1"), 1, ByteArrayInputStream(ByteArray(10)), 1000) }
            .hasMessageContaining("ended before")
    }
}
