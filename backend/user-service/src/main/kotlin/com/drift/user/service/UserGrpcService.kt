package com.drift.user.service

import com.drift.grpc.user.v1.GetPresenceRequest
import com.drift.grpc.user.v1.GetPresenceResponse
import com.drift.grpc.user.v1.GetUserRequest
import com.drift.grpc.user.v1.GetUserResponse
import com.drift.grpc.user.v1.GetUsersRequest
import com.drift.grpc.user.v1.GetUsersResponse
import com.drift.grpc.user.v1.SetPresenceRequest
import com.drift.grpc.user.v1.SetPresenceResponse
import com.drift.grpc.user.v1.UserProfile
import com.drift.grpc.user.v1.UserServiceGrpcKt
import com.drift.user.interfaces.IUserService
import com.drift.user.models.User
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.util.UUID

/** gRPC server for `user.v1.UserService` (spec section 16): profile lookups and presence for other services. */
@Service
class UserGrpcService(
    private val userService: IUserService,
) : UserServiceGrpcKt.UserServiceCoroutineImplBase() {
    override suspend fun getUser(request: GetUserRequest): GetUserResponse =
        withContext(Dispatchers.IO) {
            val id = parse(request.userId)
            val user = userService.getByIds(listOf(id)).firstOrNull()
            if (user == null) {
                GetUserResponse.newBuilder().setFound(false).build()
            } else {
                GetUserResponse
                    .newBuilder()
                    .setFound(true)
                    .setUser(user.toProfile(userService.isOnline(id)))
                    .build()
            }
        }

    override suspend fun getUsers(request: GetUsersRequest): GetUsersResponse =
        withContext(Dispatchers.IO) {
            val ids = request.userIdsList.map(::parse)
            val presence = userService.onlineStatus(ids)
            GetUsersResponse
                .newBuilder()
                .addAllUsers(userService.getByIds(ids).map { it.toProfile(presence[it.id] == true) })
                .build()
        }

    override suspend fun setPresence(request: SetPresenceRequest): SetPresenceResponse =
        withContext(Dispatchers.IO) {
            userService.setPresence(parse(request.userId), request.online)
            SetPresenceResponse.getDefaultInstance()
        }

    override suspend fun getPresence(request: GetPresenceRequest): GetPresenceResponse =
        withContext(Dispatchers.IO) {
            val ids = request.userIdsList.map(::parse)
            GetPresenceResponse
                .newBuilder()
                .putAllOnline(userService.onlineStatus(ids).mapKeys { it.key.toString() })
                .build()
        }

    private fun parse(value: String): UUID =
        runCatching { UUID.fromString(value) }
            .getOrElse { throw StatusException(Status.INVALID_ARGUMENT.withDescription("Invalid user id: $value")) }

    private fun User.toProfile(online: Boolean): UserProfile =
        UserProfile
            .newBuilder()
            .setId(id.toString())
            .setUsername(username)
            .setName(name)
            .setAvatarFileId(avatarFileId?.toString() ?: "")
            .setOnline(online)
            .build()
}
