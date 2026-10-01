package com.drift.message.socket

import com.corundumstudio.socketio.SocketIOClient
import com.drift.message.interfaces.ISocketConnection
import java.util.UUID

class SocketIoConnection(
    private val client: SocketIOClient,
    override val userId: UUID,
) : ISocketConnection {
    override val sessionId: String = client.sessionId.toString()

    override fun send(
        event: String,
        payload: Any,
    ) {
        client.sendEvent(event, SocketPayloads.toWire(payload))
    }

    override fun disconnect() {
        client.disconnect()
    }
}
