package com.drift.user.interfaces

import com.drift.user.models.SignUpResult
import com.drift.user.models.User
import java.io.InputStream
import java.util.UUID

/** User business logic (spec sections 3 and 13.1); implemented by `service/UserService`. */
interface IUserService {
    /** Sign up: validates input, stores the avatar, saves the user, issues the session JWT. */
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

    /** Presence heartbeat (spec section 4). */
    fun heartbeat(userId: UUID)

    /** Explicit presence change; publishes `presence.changed`. */
    fun setPresence(
        userId: UUID,
        online: Boolean,
    )

    fun isOnline(userId: UUID): Boolean

    fun onlineStatus(ids: Collection<UUID>): Map<UUID, Boolean>

    /** Deletes expired users and publishes `user.deleted`. */
    fun deleteExpiredUsers(): List<UUID>
}
