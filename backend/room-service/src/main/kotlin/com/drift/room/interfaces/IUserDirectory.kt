package com.drift.room.interfaces

import com.drift.room.models.UserProfile
import java.util.UUID

interface IUserDirectory {
    fun profiles(ids: Collection<UUID>): Map<UUID, UserProfile>

    fun presence(ids: Collection<UUID>): Map<UUID, Boolean>
}
