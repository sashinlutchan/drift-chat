package com.drift.room.service

import com.drift.grpc.user.v1.GetPresenceRequest
import com.drift.grpc.user.v1.GetPresenceResponse
import com.drift.grpc.user.v1.GetUsersRequest
import com.drift.grpc.user.v1.GetUsersResponse
import com.drift.grpc.user.v1.UserServiceGrpcKt
import com.drift.room.config.AppProperties
import com.drift.room.exceptions.DomainError
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.netty.NettyServerBuilder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration
import java.util.UUID
import com.drift.grpc.user.v1.UserProfile as ProtoProfile

class UserDirectoryClientTest {
    private var down = false
    private val batchSizes: MutableList<Int> = java.util.Collections.synchronizedList(mutableListOf())
    private val existing = UUID.randomUUID()

    private val fakeUsers =
        object : UserServiceGrpcKt.UserServiceCoroutineImplBase() {
            override suspend fun getUsers(request: GetUsersRequest): GetUsersResponse {
                if (down) throw StatusException(Status.UNAVAILABLE)
                batchSizes += request.userIdsCount
                return GetUsersResponse
                    .newBuilder()
                    .addAllUsers(
                        request.userIdsList.filter { it == existing.toString() }.map {
                            ProtoProfile
                                .newBuilder()
                                .setId(
                                    it,
                                ).setUsername("sipho")
                                .setName("Sipho")
                                .setAvatarFileId(UUID(0, 1).toString())
                                .setOnline(true)
                                .build()
                        },
                    ).build()
            }

            override suspend fun getPresence(request: GetPresenceRequest): GetPresenceResponse {
                if (down) throw StatusException(Status.UNAVAILABLE)
                return GetPresenceResponse
                    .newBuilder()
                    .putAllOnline(
                        request.userIdsList.associateWith { it == existing.toString() },
                    ).build()
            }
        }

    private val server: Server =
        NettyServerBuilder
            .forAddress(InetSocketAddress("localhost", 0))
            .addService(fakeUsers)
            .build()
            .start()
    private val client =
        UserDirectoryClient(
            AppProperties(
                "x".repeat(32),
                "c",
                Duration.ofHours(24),
                Duration.ofMinutes(5),
                "localhost:${server.port}",
                "https://drift.test",
            ),
        )

    @AfterEach
    fun stop() {
        client.close()
        server.shutdownNow()
    }

    @Test
    fun `profiles come back keyed by id, missing users are absent`() {
        val result = client.profiles(listOf(existing, UUID.randomUUID()))

        assertThat(result).containsOnlyKeys(existing)
        assertThat(result.getValue(existing).name).isEqualTo("Sipho")
        assertThat(result.getValue(existing).online).isTrue()
        assertThat(result.getValue(existing).avatarFileId).isEqualTo(UUID(0, 1))
    }

    @Test
    fun `big id lists are split into batches`() {
        client.profiles((1..250).map { UUID.randomUUID() })

        assertThat(batchSizes.sorted()).containsExactly(50, 100, 100)
    }

    @Test
    fun `presence maps ids to online flags`() {
        val other = UUID.randomUUID()

        assertThat(client.presence(listOf(existing, other))).containsEntry(existing, true).containsEntry(other, false)
    }

    @Test
    fun `no ids means no call`() {
        assertThat(client.profiles(emptyList())).isEmpty()
        assertThat(batchSizes).isEmpty()
    }

    @Test
    fun `user-service being down becomes a dependency error`() {
        down = true

        assertThatThrownBy { client.profiles(listOf(existing)) }.isInstanceOf(DomainError.DependencyUnavailable::class.java)
        assertThatThrownBy { client.presence(listOf(existing)) }.isInstanceOf(DomainError.DependencyUnavailable::class.java)
    }
}
