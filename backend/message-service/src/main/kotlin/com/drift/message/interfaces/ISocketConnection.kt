package com.drift.message.interfaces

import java.util.UUID

/** One live socket, as the hub sees it. Lets the hub and chat logic be tested without a real Socket.IO server. */
interface ISocketConnection {
    val sessionId: String
    val userId: UUID

    fun send(
        event: String,
        payload: Any,
    )

    fun disconnect()
}
