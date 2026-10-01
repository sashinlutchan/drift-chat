package com.drift.message.interfaces

import java.util.UUID

interface IChatService {
    fun connected(connection: ISocketConnection)

    fun disconnected(connection: ISocketConnection)

    fun enterRoom(
        connection: ISocketConnection,
        roomId: UUID,
    )

    fun exitRoom(
        connection: ISocketConnection,
        roomId: UUID,
    )

    fun sendMessage(
        connection: ISocketConnection,
        roomId: UUID,
        text: String,
        clientId: String,
    ): Map<String, Any?>

    fun typing(
        connection: ISocketConnection,
        roomId: UUID,
        typing: Boolean,
    )

    fun heartbeat(connection: ISocketConnection)

    fun sweepOccupancy(): Int
}
