package com.drift.message

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SocketIoTestClient private constructor(
    private val mapper: ObjectMapper = ObjectMapper(),
) : WebSocket.Listener {
    class Event(
        val name: String,
        val data: JsonNode,
    )

    private lateinit var socket: WebSocket
    private val buffer = StringBuilder()
    val events = CopyOnWriteArrayList<Event>()
    val closed = CompletableFuture<Int>()
    private val acks = ConcurrentHashMap<Int, CompletableFuture<JsonNode>>()
    private val nextAck = AtomicInteger(1)
    private val connected = CompletableFuture<Unit>()

    override fun onOpen(webSocket: WebSocket) {
        socket = webSocket
        webSocket.request(1)
    }

    override fun onText(
        webSocket: WebSocket,
        data: CharSequence,
        last: Boolean,
    ): CompletionStage<*>? {
        buffer.append(data)
        if (last) {
            val frame = buffer.toString()
            buffer.setLength(0)
            handle(frame)
        }
        webSocket.request(1)
        return null
    }

    private fun handle(frame: String) {
        when {
            frame == "2" -> socket.sendText("3", true)
            frame.startsWith("0") -> socket.sendText("40", true)
            frame.startsWith("40") -> connected.complete(Unit)
            frame.startsWith("44") -> connected.completeExceptionally(IllegalStateException("namespace rejected: $frame"))
            frame.startsWith("42") -> {
                val json = mapper.readTree(frame.substring(2))
                events += Event(json[0].asText(), if (json.size() > 1) json[1] else mapper.nullNode())
            }
            frame.startsWith("43") -> {
                val idEnd = frame.indexOf('[')
                val id = frame.substring(2, idEnd).toInt()
                val payload = mapper.readTree(frame.substring(idEnd))
                acks.remove(id)?.complete(if (payload.size() > 0) payload[0] else mapper.nullNode())
            }
        }
    }

    override fun onClose(
        webSocket: WebSocket,
        statusCode: Int,
        reason: String?,
    ): CompletionStage<*>? {
        closed.complete(statusCode)
        return null
    }

    override fun onError(
        webSocket: WebSocket,
        error: Throwable,
    ) {
        closed.complete(-1)
        connected.completeExceptionally(error)
    }

    fun emit(
        name: String,
        payload: Any?,
        timeoutSeconds: Long = 10,
    ): JsonNode {
        val id = nextAck.getAndIncrement()
        val future = CompletableFuture<JsonNode>()
        acks[id] = future
        socket.sendText("42$id" + mapper.writeValueAsString(listOf(name, payload)), true)
        return future.get(timeoutSeconds, TimeUnit.SECONDS)
    }

    fun emitNoAck(
        name: String,
        payload: Any? = null,
    ) {
        val args = if (payload == null) listOf(name) else listOf(name, payload)
        socket.sendText("42" + mapper.writeValueAsString(args), true)
    }

    fun await(
        name: String,
        timeoutSeconds: Long = 15,
        predicate: (JsonNode) -> Boolean = { true },
    ): JsonNode {
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000
        while (System.currentTimeMillis() < deadline) {
            events.firstOrNull { it.name == name && predicate(it.data) }?.let { return it.data }
            Thread.sleep(50)
        }
        error("No '$name' event within ${timeoutSeconds}s. Received: ${events.map { it.name }}")
    }

    fun count(name: String): Int = events.count { it.name == name }

    fun close() {
        runCatching { socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(2, TimeUnit.SECONDS) }
    }

    companion object {
        fun connect(
            port: Int,
            origin: String?,
            cookie: String?,
        ): SocketIoTestClient {
            val client = SocketIoTestClient()
            val builder = HttpClient.newHttpClient().newWebSocketBuilder()
            origin?.let { builder.header("Origin", it) }
            cookie?.let { builder.header("Cookie", it) }
            client.socket =
                builder
                    .buildAsync(
                        URI.create("ws://localhost:$port/socket.io/?EIO=4&transport=websocket"),
                        client,
                    ).get(10, TimeUnit.SECONDS)
            client.connected.get(10, TimeUnit.SECONDS)
            return client
        }
    }
}
