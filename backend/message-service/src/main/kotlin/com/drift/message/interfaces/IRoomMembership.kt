package com.drift.message.interfaces

import com.drift.message.models.RoomAccess
import java.util.UUID

interface IRoomMembership {
    fun access(
        roomId: UUID,
        userId: UUID,
    ): RoomAccess
}
