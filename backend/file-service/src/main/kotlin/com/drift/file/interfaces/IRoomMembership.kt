package com.drift.file.interfaces

import java.util.UUID

/** Asks room-service whether a user may see a room's files (spec section 13.4). Implemented over gRPC in `service/`. */
interface IRoomMembership {
    /** `null` when the room doesn't exist, otherwise whether [userId] is a member. */
    fun isMember(
        roomId: UUID,
        userId: UUID,
    ): Boolean?
}
