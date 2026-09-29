package com.drift.room.interfaces

import com.drift.room.models.UserProfile
import java.util.UUID

/** Looks users up in user-service over gRPC (spec section 13.2); implemented in `service/`. */
interface IUserDirectory {
    /** Profiles with presence for the ids that still exist (deleted users are simply absent). */
    fun profiles(ids: Collection<UUID>): Map<UUID, UserProfile>

    /** Online flags for many users at once. */
    fun presence(ids: Collection<UUID>): Map<UUID, Boolean>
}
