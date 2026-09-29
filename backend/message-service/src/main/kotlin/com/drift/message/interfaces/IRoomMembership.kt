package com.drift.message.interfaces

import com.drift.message.models.RoomAccess
import java.util.UUID

/** Asks room-service whether a user may use a room (gRPC `IsMember`, spec section 13.3). Implemented in `service/`. */
interface IRoomMembership {
    fun access(
        roomId: UUID,
        userId: UUID,
    ): RoomAccess
}
