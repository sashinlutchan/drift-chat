package com.drift.file.service

import com.drift.file.common.GrpcChannels
import com.drift.file.config.AppProperties
import com.drift.file.exceptions.DomainError
import com.drift.file.interfaces.IRoomMembership
import com.drift.grpc.room.v1.IsMemberRequest
import com.drift.grpc.room.v1.RoomServiceGrpcKt
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
class RoomMembershipClient(
    properties: AppProperties,
    observations: ObservationRegistry = ObservationRegistry.NOOP,
) : IRoomMembership {
    private val channel: ManagedChannel = GrpcChannels.create(properties.roomServiceAddr, observations)
    private val stub = RoomServiceGrpcKt.RoomServiceCoroutineStub(channel)

    override fun isMember(
        roomId: UUID,
        userId: UUID,
    ): Boolean? =
        try {
            val request =
                IsMemberRequest
                    .newBuilder()
                    .setRoomId(roomId.toString())
                    .setUserId(userId.toString())
                    .build()
            val response = runBlocking { stub.isMember(request) }
            if (response.roomExists) response.isMember else null
        } catch (e: StatusException) {
            throw DomainError.DependencyUnavailable("Could not check room membership: ${e.status.code}")
        } catch (e: StatusRuntimeException) {
            throw DomainError.DependencyUnavailable("Could not check room membership: ${e.status.code}")
        }

    @PreDestroy
    fun close() {
        channel.shutdown().awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)
    }

    private companion object {
        const val SHUTDOWN_SECONDS = 5L
    }
}
