package com.drift.message.socket

import com.corundumstudio.socketio.AckMode
import com.corundumstudio.socketio.AckRequest
import com.corundumstudio.socketio.AuthorizationListener
import com.corundumstudio.socketio.AuthorizationResult
import com.corundumstudio.socketio.Configuration
import com.corundumstudio.socketio.SocketIOClient
import com.corundumstudio.socketio.SocketIOServer
import com.corundumstudio.socketio.Transport
import com.drift.message.config.AppProperties
import com.drift.message.dto.SocketErrorDto
import com.drift.message.exceptions.DomainError
import com.drift.message.interfaces.IChatService
import com.drift.message.interfaces.IConnectionHub
import com.drift.message.interfaces.ISocketConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

/**
 * The Socket.IO v4 server (spec sections 13.3 and 15), started and stopped with the Spring lifecycle. WebSocket only,
 * so no sticky sessions are needed. A connection is accepted only with an allowed `Origin` and a valid session cookie;
 * it is closed when that session's JWT expires. Every event is handed to [IChatService] on a virtual thread, in order
 * per socket.
 */
@Component
@ConditionalOnProperty(prefix = "drift.socket", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class SocketServerLifecycle(
    private val properties: AppProperties,
    private val authenticator: SocketAuthenticator,
    private val chat: IChatService,
    private val hub: IConnectionHub,
    private val meters: MeterRegistry,
    private val clock: Clock,
) : SmartLifecycle {
    private val scope = CoroutineScope(SupervisorJob() + Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher())
    private val dispatcher = SocketDispatcher(scope)
    private val expiry: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor {
            Thread(it, "socket-expiry").apply {
                isDaemon =
                    true
            }
        }
    private val expiryTasks = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val connections = ConcurrentHashMap<UUID, SocketIoConnection>()

    @Volatile private var server: SocketIOServer? = null

    override fun start() {
        val config =
            Configuration().apply {
                hostname = "0.0.0.0"
                port = properties.socket.port
                origin = properties.socket.allowedOrigins.firstOrNull()
                setTransports(Transport.WEBSOCKET)
                isAllowCustomRequests = false
                // We answer acks ourselves, after the work is done on another thread; the default would send an empty ack straight away
                ackMode = AckMode.MANUAL
                pingInterval =
                    properties.socket.pingInterval
                        .toMillis()
                        .toInt()
                pingTimeout =
                    properties.socket.pingTimeout
                        .toMillis()
                        .toInt()
                authorizationListener =
                    AuthorizationListener { handshake ->
                        val auth = authenticator.authenticate(handshake.httpHeaders["Origin"], handshake.httpHeaders["Cookie"])
                        if (auth is SocketAuth.Rejected) log.info { "Socket handshake rejected: ${auth.reason}" }
                        when (auth) {
                            is SocketAuth.Accepted -> AuthorizationResult.SUCCESSFUL_AUTHORIZATION
                            is SocketAuth.Rejected -> AuthorizationResult.FAILED_AUTHORIZATION
                        }
                    }
            }
        val socketServer = SocketIOServer(config)
        registerListeners(socketServer)
        socketServer.start()
        server = socketServer
        Gauge.builder("drift.sockets.active", hub) { it.connectionCount().toDouble() }.register(meters)
        Gauge.builder("drift.rooms.occupied", hub) { it.occupiedRoomCount().toDouble() }.register(meters)
        log.info { "Socket.IO server listening on ${properties.socket.port}" }
    }

    private fun registerListeners(socketServer: SocketIOServer) {
        socketServer.addConnectListener { client -> onConnect(client) }
        socketServer.addDisconnectListener { client -> onDisconnect(client) }

        socketServer.addEventListener("room:enter", String::class.java) { client, roomId, ack ->
            handle(client, ack, "room:enter") { conn -> chat.enterRoom(conn, uuid(roomId)) }
        }
        socketServer.addEventListener("room:exit", String::class.java) { client, roomId, ack ->
            handle(client, ack, "room:exit") { conn -> chat.exitRoom(conn, uuid(roomId)) }
        }
        socketServer.addEventListener("message:send", Map::class.java) { client, data, ack ->
            handle(client, ack, "message:send") { conn ->
                val ackPayload =
                    chat.sendMessage(
                        conn,
                        uuid(data["roomId"] as? String),
                        data["text"] as? String ?: "",
                        data["clientId"] as? String ?: "",
                    )
                conn.send("message:ack", ackPayload)
                ackPayload
            }
        }
        socketServer.addEventListener("typing:start", String::class.java) { client, roomId, ack ->
            handle(client, ack, "typing:start") { conn -> chat.typing(conn, uuid(roomId), true) }
        }
        socketServer.addEventListener("typing:stop", String::class.java) { client, roomId, ack ->
            handle(client, ack, "typing:stop") { conn -> chat.typing(conn, uuid(roomId), false) }
        }
        socketServer.addEventListener("presence:heartbeat", Any::class.java) { client, _, ack ->
            handle(client, ack, "presence:heartbeat") { conn -> chat.heartbeat(conn) }
        }
    }

    private fun onConnect(client: SocketIOClient) {
        val auth = authenticator.authenticate(client.handshakeData.httpHeaders["Origin"], client.handshakeData.httpHeaders["Cookie"])
        if (auth !is SocketAuth.Accepted) {
            client.disconnect()
            return
        }
        val connection = SocketIoConnection(client, auth.userId)
        connections[client.sessionId] = connection
        dispatcher.submit(connection.sessionId) { chat.connected(connection) }
        // The session ends when its JWT does: close the socket, the client's next reconnect gets a 401 (spec section 3.3)
        val delay = Duration.between(clock.instant(), auth.expiresAt).toMillis().coerceAtLeast(0)
        expiryTasks[connection.sessionId] = expiry.schedule({ runCatching { client.disconnect() } }, delay, TimeUnit.MILLISECONDS)
    }

    private fun onDisconnect(client: SocketIOClient) {
        val connection = connections.remove(client.sessionId) ?: return
        expiryTasks.remove(connection.sessionId)?.cancel(false)
        dispatcher.submit(connection.sessionId) { chat.disconnected(connection) }
        dispatcher.close(connection.sessionId)
    }

    /** Runs one event for one socket in order, replies to the ack if asked, and turns any failure into an `error` event. */
    private fun handle(
        client: SocketIOClient,
        ack: AckRequest,
        event: String,
        action: (ISocketConnection) -> Any?,
    ) {
        val connection = connections[client.sessionId]
        if (connection == null) {
            if (ack.isAckRequested) {
                ack.sendAckData(
                    SocketPayloads.toWire(
                        mapOf(
                            "ok" to false,
                            "error" to SocketErrorDto(event, "UNAUTHORIZED", "Not connected."),
                        ),
                    ),
                )
            }
            return
        }
        dispatcher.submit(connection.sessionId) {
            try {
                val result = action(connection)
                if (ack.isAckRequested) {
                    ack.sendAckData(
                        SocketPayloads.toWire(mapOf("ok" to true, "data" to result.takeUnless { it is Unit })),
                    )
                }
            } catch (e: DomainError) {
                reply(connection, ack, SocketErrorDto(event, e.code.name, e.detail))
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                log.error(e) { "Unexpected error handling $event" }
                reply(connection, ack, SocketErrorDto(event, "INTERNAL_ERROR", "Something went wrong. Please try again."))
            }
        }
    }

    private fun reply(
        connection: ISocketConnection,
        ack: AckRequest,
        error: SocketErrorDto,
    ) {
        connection.send("error", error)
        if (ack.isAckRequested) ack.sendAckData(SocketPayloads.toWire(mapOf("ok" to false, "error" to error)))
    }

    private fun uuid(value: String?): UUID =
        runCatching {
            UUID.fromString(value)
        }.getOrElse { throw DomainError.ValidationFailed("Invalid room id.") }

    /** Graceful shutdown: tidy up every socket's occupancy and presence, tell clients, then stop listening. */
    override fun stop() {
        val running = server ?: return
        server = null
        connections.values.toList().forEach { runCatching { chat.disconnected(it) } }
        hub.disconnectAll()
        expiry.shutdownNow()
        running.stop()
        scope.cancel()
        log.info { "Socket.IO server stopped" }
    }

    override fun isRunning(): Boolean = server != null

    override fun getPhase(): Int = Int.MAX_VALUE - 1
}
