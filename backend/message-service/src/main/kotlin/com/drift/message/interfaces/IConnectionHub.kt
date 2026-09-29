package com.drift.message.interfaces

import java.util.UUID

/** Thread-safe registry of this pod's sockets and which rooms each has open (spec section 13.3). */
interface IConnectionHub {
    fun register(connection: ISocketConnection)

    /** Removes the socket and returns the rooms it still had open. */
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

    /** Takes every socket of [userId] out of [roomId]; returns the sockets that were inside. */
    fun evictUser(
        roomId: UUID,
        userId: UUID,
    ): List<ISocketConnection>

    /** Takes every socket out of [roomId]; returns them. */
    fun clearRoom(roomId: UUID): List<ISocketConnection>

    fun connectionCount(): Int

    /** How many rooms have at least one socket inside on this pod. */
    fun occupiedRoomCount(): Int

    fun disconnectAll()
}
