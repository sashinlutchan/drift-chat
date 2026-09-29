package com.drift.message.interfaces

import com.drift.message.models.UserProfile
import java.util.UUID

/** user-service over gRPC (spec section 13.3): profiles for message senders, and presence reporting. */
interface IUserDirectory {
    /** Profiles for the ids that still exist (deleted users are absent). */
    fun profiles(ids: Collection<UUID>): Map<UUID, UserProfile>

    fun setPresence(
        userId: UUID,
        online: Boolean,
    )
}
