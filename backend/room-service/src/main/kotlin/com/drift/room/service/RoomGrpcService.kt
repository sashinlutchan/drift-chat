package com.drift.room.service

import com.drift.grpc.room.v1.GetRoomRequest
import com.drift.grpc.room.v1.GetRoomResponse
import com.drift.grpc.room.v1.IsMemberRequest
import com.drift.grpc.room.v1.IsMemberResponse
import com.drift.grpc.room.v1.RoomServiceGrpcKt
import com.drift.room.exceptions.DomainError
import com.drift.room.exceptions.grpcGuard
import com.drift.room.interfaces.IRoomService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.util.UUID

/** gRPC server for `room.v1.RoomService` (spec section 16): membership checks for message-service and file-service. */
@Service
class RoomGrpcService(
    private val rooms: IRoomService,
) : RoomServiceGrpcKt.RoomServiceCoroutineImplBase() {
    override suspend fun isMember(request: IsMemberRequest): IsMemberResponse =
        grpcGuard {
            val member = withContext(Dispatchers.IO) { rooms.isMember(parse(request.roomId), parse(request.userId)) }
            IsMemberResponse
                .newBuilder()
                .setRoomExists(member != null)
                .setIsMember(member == true)
                .build()
        }

    override suspend fun getRoom(request: GetRoomRequest): GetRoomResponse =
        grpcGuard {
            val room = withContext(Dispatchers.IO) { rooms.find(parse(request.roomId)) }
            if (room == null) {
                GetRoomResponse.newBuilder().setFound(false).build()
            } else {
                GetRoomResponse
                    .newBuilder()
                    .setFound(true)
                    .setId(room.id.toString())
                    .setName(room.name)
                    .setSlug(room.slug)
                    .setCreatedBy(room.createdBy?.toString().orEmpty())
                    .build()
            }
        }

    private fun parse(value: String): UUID =
        runCatching { UUID.fromString(value) }.getOrElse { throw DomainError.ValidationFailed("Invalid id: $value") }
}
