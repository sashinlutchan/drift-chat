package com.drift.message.interfaces

import java.util.UUID

/** What a connected socket can do (spec section 15 client → server events); implemented by `service/ChatService`. */
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

    /** Returns the `message:ack` payload for the sender. */
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

    /** Drops occupancy entries whose socket stopped heartbeating (a crashed pod) and announces rooms that emptied. */
    fun sweepOccupancy(): Int
}
