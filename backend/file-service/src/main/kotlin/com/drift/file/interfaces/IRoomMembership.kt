package com.drift.file.interfaces

import java.util.UUID

interface IRoomMembership {
    fun isMember(
        roomId: UUID,
        userId: UUID,
    ): Boolean?
}
