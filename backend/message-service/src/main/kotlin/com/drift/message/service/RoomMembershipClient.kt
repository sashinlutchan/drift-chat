package com.drift.message.service

import com.drift.grpc.room.v1.IsMemberRequest
import com.drift.grpc.room.v1.RoomServiceGrpcKt
import com.drift.message.common.GrpcChannels
import com.drift.message.config.AppProperties
import com.drift.message.exceptions.DomainError
import com.drift.message.interfaces.IRoomMembership
import com.drift.message.models.RoomAccess
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

    override fun access(
        roomId: UUID,
        userId: UUID,
    ): RoomAccess =
        try {
            val request =
                IsMemberRequest
                    .newBuilder()
                    .setRoomId(roomId.toString())
                    .setUserId(userId.toString())
                    .build()
            val response = runBlocking { stub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS).isMember(request) }
            when {
                !response.roomExists -> RoomAccess.NO_SUCH_ROOM
                response.isMember -> RoomAccess.MEMBER
                else -> RoomAccess.NOT_MEMBER
            }
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
        const val DEADLINE_SECONDS = 5L
        const val SHUTDOWN_SECONDS = 5L
    }
}
