package com.drift.user.service

import com.drift.grpc.user.v1.GetPresenceRequest
import com.drift.grpc.user.v1.GetUserRequest
import com.drift.grpc.user.v1.GetUsersRequest
import com.drift.grpc.user.v1.SetPresenceRequest
import com.drift.user.interfaces.IUserService
import com.drift.user.models.User
import io.grpc.StatusException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class UserGrpcServiceTest {
    private val users = mockk<IUserService>()
    private val grpc = UserGrpcService(users)
    private val id = UUID.randomUUID()
    private val user = User(id, "sipho", "Sipho", "s@b.co", null, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH)

    @Test
    fun `getUser returns the profile with presence`() =
        runBlocking {
            every { users.getByIds(listOf(id)) } returns listOf(user)
            every { users.isOnline(id) } returns true

            val response = grpc.getUser(GetUserRequest.newBuilder().setUserId(id.toString()).build())

            assertThat(response.found).isTrue()
            assertThat(response.user.username).isEqualTo("sipho")
            assertThat(response.user.online).isTrue()
            assertThat(response.user.avatarFileId).isEmpty()
        }

    @Test
    fun `getUser reports not found`() =
        runBlocking {
            every { users.getByIds(any()) } returns emptyList()
            assertThat(grpc.getUser(GetUserRequest.newBuilder().setUserId(id.toString()).build()).found).isFalse()
        }

    @Test
    fun `getUser rejects a malformed id`() {
        assertThatThrownBy { runBlocking { grpc.getUser(GetUserRequest.newBuilder().setUserId("nope").build()) } }
            .isInstanceOf(StatusException::class.java)
    }

    @Test
    fun `getUsers returns profiles for existing users only`() =
        runBlocking {
            every { users.getByIds(listOf(id)) } returns listOf(user)
            every { users.onlineStatus(listOf(id)) } returns mapOf(id to false)

            val response = grpc.getUsers(GetUsersRequest.newBuilder().addUserIds(id.toString()).build())

            assertThat(response.usersList).hasSize(1)
            assertThat(response.usersList[0].online).isFalse()
        }

    @Test
    fun `setPresence delegates to the service`() =
        runBlocking {
            every { users.setPresence(id, true) } returns Unit

            grpc.setPresence(
                SetPresenceRequest
                    .newBuilder()
                    .setUserId(id.toString())
                    .setOnline(true)
                    .build(),
            )

            verify { users.setPresence(id, true) }
        }

    @Test
    fun `getPresence maps ids to online flags`() =
        runBlocking {
            every { users.onlineStatus(listOf(id)) } returns mapOf(id to true)

            val response = grpc.getPresence(GetPresenceRequest.newBuilder().addUserIds(id.toString()).build())

            assertThat(response.onlineMap).containsEntry(id.toString(), true)
        }
}
