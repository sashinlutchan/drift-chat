package com.drift.message

import com.drift.message.interfaces.ISocketConnection
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** A fake socket that records what it was sent. */
class TestConnection(
    override val userId: UUID = UUID.randomUUID(),
    override val sessionId: String = UUID.randomUUID().toString(),
) : ISocketConnection {
    val sent = CopyOnWriteArrayList<Pair<String, Any>>()
    var disconnected = false
    var failOnSend = false

    override fun send(
        event: String,
        payload: Any,
    ) {
        if (failOnSend) error("socket is broken")
        sent += event to payload
    }

    override fun disconnect() {
        disconnected = true
    }

    fun events(): List<String> = sent.map { it.first }
}
