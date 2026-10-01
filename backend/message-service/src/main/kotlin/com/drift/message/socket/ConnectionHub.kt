package com.drift.message.socket

import com.drift.message.interfaces.IConnectionHub
import com.drift.message.interfaces.ISocketConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

@Component
class ConnectionHub : IConnectionHub {
    private val connections = ConcurrentHashMap<String, ISocketConnection>()
    private val roomsBySession = ConcurrentHashMap<String, MutableSet<UUID>>()
    private val sessionsByRoom = ConcurrentHashMap<UUID, MutableSet<String>>()

    override fun register(connection: ISocketConnection) {
        connections[connection.sessionId] = connection
        roomsBySession.putIfAbsent(connection.sessionId, ConcurrentHashMap.newKeySet())
    }

    override fun unregister(sessionId: String): Set<UUID> {
        connections.remove(sessionId)
        val rooms = roomsBySession.remove(sessionId).orEmpty().toSet()
        rooms.forEach { removeFromRoom(sessionId, it) }
        return rooms
    }

    override fun find(sessionId: String): ISocketConnection? = connections[sessionId]

    override fun enter(
        sessionId: String,
        roomId: UUID,
    ): Boolean {
        val rooms = roomsBySession[sessionId] ?: return false
        val added = rooms.add(roomId)
        sessionsByRoom.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }.add(sessionId)
        return added
    }

    override fun exit(
        sessionId: String,
        roomId: UUID,
    ): Boolean {
        val removed = roomsBySession[sessionId]?.remove(roomId) ?: false
        removeFromRoom(sessionId, roomId)
        return removed
    }

    override fun isInside(
        sessionId: String,
        roomId: UUID,
    ): Boolean = roomsBySession[sessionId]?.contains(roomId) == true

    override fun roomsOf(sessionId: String): Set<UUID> = roomsBySession[sessionId].orEmpty().toSet()

    override fun sendToRoom(
        roomId: UUID,
        event: String,
        payload: Any,
    ) {
        sessionsByRoom[roomId]
            .orEmpty()
            .toList()
            .mapNotNull { connections[it] }
            .forEach { safeSend(it, event, payload) }
    }

    override fun sendToAll(
        event: String,
        payload: Any,
    ) {
        connections.values.forEach { safeSend(it, event, payload) }
    }

    override fun evictUser(
        roomId: UUID,
        userId: UUID,
    ): List<ISocketConnection> =
        sessionsByRoom[roomId]
            .orEmpty()
            .toList()
            .mapNotNull { connections[it] }
            .filter { it.userId == userId }
            .onEach { exit(it.sessionId, roomId) }

    override fun clearRoom(roomId: UUID): List<ISocketConnection> =
        sessionsByRoom
            .remove(roomId)
            .orEmpty()
            .toList()
            .mapNotNull { connections[it] }
            .onEach { roomsBySession[it.sessionId]?.remove(roomId) }

    override fun connectionCount(): Int = connections.size

    override fun occupiedRoomCount(): Int = sessionsByRoom.size

    override fun disconnectAll() {
        connections.values.toList().forEach { runCatching { it.disconnect() } }
    }

    private fun removeFromRoom(
        sessionId: String,
        roomId: UUID,
    ) {
        sessionsByRoom[roomId]?.let { sessions ->
            sessions.remove(sessionId)
            if (sessions.isEmpty()) sessionsByRoom.remove(roomId, sessions)
        }
    }

    private fun safeSend(
        connection: ISocketConnection,
        event: String,
        payload: Any,
    ) {
        try {
            connection.send(event, payload)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.warn { "Could not send $event to ${connection.sessionId}: ${e.message}" }
        }
    }
}
