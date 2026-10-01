package com.drift.message.interfaces

import java.util.UUID

interface IConnectionHub {
    fun register(connection: ISocketConnection)

    fun unregister(sessionId: String): Set<UUID>

    fun find(sessionId: String): ISocketConnection?

    fun enter(
        sessionId: String,
        roomId: UUID,
    ): Boolean

    fun exit(
        sessionId: String,
        roomId: UUID,
    ): Boolean

    fun isInside(
        sessionId: String,
        roomId: UUID,
    ): Boolean

    fun roomsOf(sessionId: String): Set<UUID>

    fun sendToRoom(
        roomId: UUID,
        event: String,
        payload: Any,
    )

    fun sendToAll(
        event: String,
        payload: Any,
    )

    fun evictUser(
        roomId: UUID,
        userId: UUID,
    ): List<ISocketConnection>

    fun clearRoom(roomId: UUID): List<ISocketConnection>

    fun connectionCount(): Int

    fun occupiedRoomCount(): Int

    fun disconnectAll()
}
