package com.drift.message.socket

import com.corundumstudio.socketio.AckRequest
import com.corundumstudio.socketio.Configuration
import com.corundumstudio.socketio.SocketIOClient
import com.corundumstudio.socketio.SocketIOServer
import com.corundumstudio.socketio.listener.DataListener
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 0 spike: proves netty-socketio 2.0.x speaks the Socket.IO v4 protocol
 * (connect with cookie from the handshake, join rooms, emit, receive, reconnect)
 * and starts/stops cleanly alongside Spring. This becomes the real socket layer
 * in Phase 7.
 */
class DriftSocketServer(
    private val port: Int,
    private val allowedOrigins: String,
) {
    private lateinit var server: SocketIOServer

    /** Every connected client's handshake data, keyed by session id (for tests/spike assertions). */
    val connectedSessions = ConcurrentHashMap<String, HandshakeSnapshot>()

    /** Events received from clients, in order (for tests/spike assertions). */
    val receivedEvents = ConcurrentHashMap.newKeySet<String>()

    data class HandshakeSnapshot(
        val url: String,
        val cookies: List<String>,
        val origin: String?,
    )

    fun start() {
        val config =
            Configuration().apply {
                port = this@DriftSocketServer.port
                origin = allowedOrigins
                isAllowCustomRequests = false
            }
        server = SocketIOServer(config)

        // Echo handler: client sends "spike:ping" with a payload, server replies via ack.
        server.addEventListener(
            "spike:ping",
            String::class.java,
            DataListener<String> { client: SocketIOClient, data: String?, ackRequest: AckRequest ->
                receivedEvents.add("ping:$data")
                recordHandshake(client)
                ackRequest.sendAckData("pong:$data")
            },
        )

        // Room handler: join a room and broadcast back into it.
        server.addEventListener(
            "spike:join",
            String::class.java,
            DataListener<String> { client: SocketIOClient, room: String?, ackRequest: AckRequest ->
                val roomName = room ?: return@DataListener
                client.joinRoom(roomName)
                recordHandshake(client)
                server.getRoomOperations(roomName).sendEvent("spike:room", "joined:$roomName")
                ackRequest.sendAckData("ok")
            },
        )

        server.start()
    }

    private fun recordHandshake(client: SocketIOClient) {
        val handshake = client.handshakeData
        connectedSessions[client.sessionId.toString()] =
            HandshakeSnapshot(
                url = handshake.url.toString(),
                cookies = handshake.httpHeaders.entries()
                    .filter { it.key.equals("Cookie", ignoreCase = true) }
                    .flatMap { it.value.split(";") }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
                origin = handshake.httpHeaders.entries()
                    .firstOrNull { it.key.equals("Origin", ignoreCase = true) }
                    ?.value
                    ?.firstOrNull()
                    ?.toString(),
            )
    }

    /** Closes every client connection — used by the reconnect part of the Phase 0 spike. */
    fun forceDisconnectAll() {
        server.allClients.forEach { it.disconnect() }
    }

    fun stop() {
        server.stop()
    }
}
