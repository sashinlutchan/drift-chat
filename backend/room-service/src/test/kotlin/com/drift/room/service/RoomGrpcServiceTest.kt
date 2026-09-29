package com.drift.room.service

import com.drift.grpc.room.v1.GetRoomRequest
import com.drift.grpc.room.v1.IsMemberRequest
import com.drift.room.interfaces.IRoomService
import com.drift.room.models.Room
import io.grpc.Status
import io.grpc.StatusException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class RoomGrpcServiceTest {
    private val rooms = mockk<IRoomService>()
    private val grpc = RoomGrpcService(rooms)
    private val roomId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    private fun request() =
        IsMemberRequest
            .newBuilder()
            .setRoomId(roomId.toString())
            .setUserId(userId.toString())
            .build()

    @Test
    fun `member, non member and missing room are told apart`() =
        runBlocking {
            every { rooms.isMember(roomId, userId) } returns true
            assertThat(grpc.isMember(request()).isMember).isTrue()
            assertThat(grpc.isMember(request()).roomExists).isTrue()

            every { rooms.isMember(roomId, userId) } returns false
            assertThat(grpc.isMember(request()).isMember).isFalse()
            assertThat(grpc.isMember(request()).roomExists).isTrue()

            every { rooms.isMember(roomId, userId) } returns null
            assertThat(grpc.isMember(request()).roomExists).isFalse()
            assertThat(grpc.isMember(request()).isMember).isFalse()
        }

    @Test
    fun `get room returns details or not found`() =
        runBlocking {
            val room = Room(roomId, "Chat", "chat-abc123", userId, Instant.EPOCH, Instant.EPOCH, null)
            every { rooms.find(roomId) } returns room
            every { rooms.find(neq(roomId)) } returns null

            val found = grpc.getRoom(GetRoomRequest.newBuilder().setRoomId(roomId.toString()).build())
            val missing = grpc.getRoom(GetRoomRequest.newBuilder().setRoomId(UUID.randomUUID().toString()).build())

            assertThat(found.found).isTrue()
            assertThat(found.slug).isEqualTo("chat-abc123")
            assertThat(found.createdBy).isEqualTo(userId.toString())
            assertThat(missing.found).isFalse()
        }

    @Test
    fun `a room whose creator was deleted has an empty created-by`() =
        runBlocking {
            every { rooms.find(roomId) } returns Room(roomId, "Chat", "s", null, Instant.EPOCH, Instant.EPOCH, null)

            assertThat(grpc.getRoom(GetRoomRequest.newBuilder().setRoomId(roomId.toString()).build()).createdBy).isEmpty()
        }

    @Test
    fun `malformed ids are invalid arguments`() {
        assertThatThrownBy {
            runBlocking {
                grpc.isMember(
                    IsMemberRequest
                        .newBuilder()
                        .setRoomId("nope")
                        .setUserId(userId.toString())
                        .build(),
                )
            }
        }.isInstanceOfSatisfying(StatusException::class.java) { assertThat(it.status.code).isEqualTo(Status.Code.INVALID_ARGUMENT) }
    }
}
