package com.drift.message.interfaces

import java.util.UUID

interface ISocketConnection {
    val sessionId: String
    val userId: UUID

    fun send(
        event: String,
        payload: Any,
    )

    fun disconnect()
}
