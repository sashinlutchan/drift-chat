package com.drift.room.service

import com.drift.grpc.user.v1.GetPresenceRequest
import com.drift.grpc.user.v1.GetUsersRequest
import com.drift.grpc.user.v1.UserServiceGrpcKt
import com.drift.room.common.GrpcChannels
import com.drift.room.config.AppProperties
import com.drift.room.exceptions.DomainError
import com.drift.room.interfaces.IUserDirectory
import com.drift.room.models.UserProfile
import io.grpc.ManagedChannel
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import io.micrometer.observation.ObservationRegistry
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    override fun profiles(ids: Collection<UUID>): Map<UUID, UserProfile> =
        call {
            coroutineScope {
                ids
                    .distinct()
                    .chunked(BATCH)
                    .map { batch ->
                        async { stub.getUsers(GetUsersRequest.newBuilder().addAllUserIds(batch.map(UUID::toString)).build()).usersList }
                    }.awaitAll()
                    .flatten()
                    .associate {
                        val id = UUID.fromString(it.id)
                        id to
                            UserProfile(
                                id,
                                it.username,
                                it.name,
                                it.avatarFileId
                                    .takeIf { f ->
                                        f.isNotBlank()
                                    }?.let(UUID::fromString),
                                it.online,
                            )
                    }
            }
        }

    override fun presence(ids: Collection<UUID>): Map<UUID, Boolean> =
        call {
            coroutineScope {
                ids
                    .distinct()
                    .chunked(BATCH)
                    .map { batch ->
                        async {
                            stub
                                .getPresence(
                                    GetPresenceRequest.newBuilder().addAllUserIds(batch.map(UUID::toString)).build(),
                                ).onlineMap
                        }
                    }.awaitAll()
                    .flatMap { it.entries }
                    .associate { UUID.fromString(it.key) to it.value }
            }
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
        const val BATCH = 100
        const val SHUTDOWN_SECONDS = 5L
    }
}
