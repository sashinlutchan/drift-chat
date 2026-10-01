package com.drift.message.interfaces

import com.drift.message.models.UserProfile
import java.util.UUID

interface IUserDirectory {
    fun profiles(ids: Collection<UUID>): Map<UUID, UserProfile>

    fun setPresence(
        userId: UUID,
        online: Boolean,
    )
}
