package com.drift.file.service

import com.drift.file.config.AppProperties
import com.drift.file.exceptions.DomainError
import com.drift.grpc.room.v1.IsMemberRequest
import com.drift.grpc.room.v1.IsMemberResponse
import com.drift.grpc.room.v1.RoomServiceGrpcKt
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.netty.NettyServerBuilder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.util.unit.DataSize
import java.net.InetSocketAddress
import java.util.UUID

class RoomMembershipClientTest {
    private val member = UUID.randomUUID()
    private val existingRoom = UUID.randomUUID()
    private var down = false

    private val fakeRooms =
        object : RoomServiceGrpcKt.RoomServiceCoroutineImplBase() {
            override suspend fun isMember(request: IsMemberRequest): IsMemberResponse {
                if (down) throw StatusException(Status.UNAVAILABLE)
                val exists = request.roomId == existingRoom.toString()
                return IsMemberResponse
                    .newBuilder()
                    .setRoomExists(exists)
                    .setIsMember(exists && request.userId == member.toString())
                    .build()
            }
        }

    private val server: Server =
        NettyServerBuilder
            .forAddress(InetSocketAddress("localhost", 0))
            .addService(fakeRooms)
            .build()
            .start()
    private val client =
        RoomMembershipClient(
            AppProperties(
                jwtSecret = "x".repeat(32),
                sessionCookieName = "c",
                maxAvatarSize = DataSize.ofMegabytes(1),
                roomServiceAddr = "localhost:${server.port}",
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
            ),
        )

    @AfterEach
    fun stop() {
        client.close()
        server.shutdownNow()
    }

    @Test
    fun `member true, non member false, unknown room null`() {
        assertThat(client.isMember(existingRoom, member)).isTrue()
        assertThat(client.isMember(existingRoom, UUID.randomUUID())).isFalse()
        assertThat(client.isMember(UUID.randomUUID(), member)).isNull()
    }

    @Test
    fun `room-service being down becomes a 503-style domain error`() {
        down = true
        assertThatThrownBy { client.isMember(existingRoom, member) }.isInstanceOf(DomainError.DependencyUnavailable::class.java)
    }
}
