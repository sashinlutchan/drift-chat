package com.drift.message.service

import com.drift.grpc.user.v1.GetUsersRequest
import com.drift.grpc.user.v1.SetPresenceRequest
import com.drift.grpc.user.v1.UserServiceGrpcKt
import com.drift.message.common.GrpcChannels
import com.drift.message.config.AppProperties
import com.drift.message.exceptions.DomainError
import com.drift.message.interfaces.IUserDirectory
import com.drift.message.models.UserProfile
import io.grpc.ManagedChannel
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import io.micrometer.observation.ObservationRegistry
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
class UserDirectoryClient(
    properties: AppProperties,
    observations: ObservationRegistry = ObservationRegistry.NOOP,
) : IUserDirectory {
    private val channel: ManagedChannel = GrpcChannels.create(properties.userServiceAddr, observations)
    private val stub = UserServiceGrpcKt.UserServiceCoroutineStub(channel)

    override fun profiles(ids: Collection<UUID>): Map<UUID, UserProfile> {
        if (ids.isEmpty()) return emptyMap()
        val request = GetUsersRequest.newBuilder().addAllUserIds(ids.distinct().map(UUID::toString)).build()
        return call { stub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS).getUsers(request) }
            .usersList
            .associate {
                val id = UUID.fromString(it.id)
                id to
                    UserProfile(id, it.username, it.name, it.avatarFileId.takeIf { f -> f.isNotBlank() }?.let(UUID::fromString), it.online)
            }
    }

    override fun setPresence(
        userId: UUID,
        online: Boolean,
    ) {
        val request =
            SetPresenceRequest
                .newBuilder()
                .setUserId(userId.toString())
                .setOnline(online)
                .build()
        call { stub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS).setPresence(request) }
    }

    @PreDestroy
    fun close() {
        channel.shutdown().awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)
    }

    private fun <T> call(block: suspend () -> T): T =
        try {
            runBlocking { block() }
        } catch (e: StatusException) {
            throw DomainError.DependencyUnavailable("user-service is unavailable: ${e.status.code}")
        } catch (e: StatusRuntimeException) {
            throw DomainError.DependencyUnavailable("user-service is unavailable: ${e.status.code}")
        }

    private companion object {
        const val DEADLINE_SECONDS = 5L
        const val SHUTDOWN_SECONDS = 5L
    }
}
