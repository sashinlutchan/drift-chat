package com.drift.user.interfaces

import com.drift.user.models.SignUpResult
import com.drift.user.models.User
import java.io.InputStream
import java.util.UUID

interface IUserService {
    @Suppress("LongParameterList")
    fun signUp(
        username: String,
        name: String,
        email: String,
        avatarFileName: String?,
        avatarContentType: String?,
        avatarContent: InputStream,
        avatarSize: Long,
    ): SignUpResult

    fun getById(id: UUID): User

    fun getByIds(ids: Collection<UUID>): List<User>

    fun usernameAvailable(username: String): Boolean

    @Suppress("LongParameterList")
    fun updateProfile(
        id: UUID,
        name: String?,
        email: String?,
        avatarFileName: String?,
        avatarContentType: String?,
        avatarContent: InputStream?,
        avatarSize: Long,
    ): User

    fun heartbeat(userId: UUID)

    fun setPresence(
        userId: UUID,
        online: Boolean,
    )

    fun isOnline(userId: UUID): Boolean

    fun onlineStatus(ids: Collection<UUID>): Map<UUID, Boolean>

    fun deleteExpiredUsers(): List<UUID>
}
