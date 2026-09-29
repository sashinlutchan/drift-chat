package com.drift.message.service

import com.drift.message.common.EventEnvelope
import com.drift.message.exceptions.DomainError
import com.drift.message.interfaces.IChatService
import com.drift.message.interfaces.IConnectionHub
import com.drift.message.interfaces.IEventPublisher
import com.drift.message.interfaces.IMessageService
import com.drift.message.interfaces.IOccupancyStore
import com.drift.message.interfaces.IRoomMembership
import com.drift.message.interfaces.ISocketConnection
import com.drift.message.interfaces.ISocketPresence
import com.drift.message.interfaces.ITypingStore
import com.drift.message.interfaces.IUserDirectory
import com.drift.message.models.RoomAccess
import com.drift.message.models.RoomOccupancyPayload
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * What a connected socket can do (spec sections 7 and 15). Enforces the rules — membership before entering a room,
 * being inside a room before sending or typing — and keeps occupancy and presence in step so rooms get their idle
 * countdown and users their online dot.
 */
@Service
class ChatService(
    private val hub: IConnectionHub,
    private val rooms: IRoomMembership,
    private val messages: IMessageService,
    private val occupancy: IOccupancyStore,
    private val presence: ISocketPresence,
    private val typing: ITypingStore,
    private val users: IUserDirectory,
    private val events: IEventPublisher,
    private val clock: Clock,
) : IChatService {
    override fun connected(connection: ISocketConnection) {
        hub.register(connection)
        if (presence.connect(connection.userId, connection.sessionId, clock.instant())) reportPresence(connection.userId, true)
    }

    override fun disconnected(connection: ISocketConnection) {
        if (hub.find(connection.sessionId) == null) return // already cleaned up (for example during shutdown)
        val open = hub.unregister(connection.sessionId)
        open.forEach { roomId -> leaveRoomBookkeeping(connection, roomId) }
        if (presence.disconnect(connection.userId, connection.sessionId, clock.instant())) reportPresence(connection.userId, false)
    }

    override fun enterRoom(
        connection: ISocketConnection,
        roomId: UUID,
    ) {
        when (rooms.access(roomId, connection.userId)) {
            RoomAccess.NO_SUCH_ROOM -> throw DomainError.NotFound("This room no longer exists.")
            RoomAccess.NOT_MEMBER -> throw DomainError.Forbidden("Join this room first.")
            RoomAccess.MEMBER -> Unit
        }
        if (hub.enter(connection.sessionId, roomId)) {
            publishOccupancy(roomId, occupancy.enter(roomId, connection.userId, connection.sessionId, clock.instant()))
        }
    }

    override fun exitRoom(
        connection: ISocketConnection,
        roomId: UUID,
    ) {
        if (hub.exit(connection.sessionId, roomId)) leaveRoomBookkeeping(connection, roomId)
    }

    override fun sendMessage(
        connection: ISocketConnection,
        roomId: UUID,
        text: String,
        clientId: String,
    ): Map<String, Any?> {
        requireInside(connection, roomId)
        val message = messages.sendText(roomId, connection.userId, text, clientId)
        // Sending ends "typing…"
        typing.stop(roomId, connection.userId)
        typing.announce(roomId)
        return mapOf("clientId" to clientId, "id" to message.id.toString(), "createdAt" to message.createdAt.toString())
    }

    override fun typing(
        connection: ISocketConnection,
        roomId: UUID,
        typing: Boolean,
    ) {
        requireInside(connection, roomId)
        if (typing) this.typing.start(roomId, connection.userId, clock.instant()) else this.typing.stop(roomId, connection.userId)
        this.typing.announce(roomId)
    }

    override fun heartbeat(connection: ISocketConnection) {
        val now = clock.instant()
        hub.roomsOf(connection.sessionId).forEach { occupancy.touch(it, connection.userId, connection.sessionId, now) }
        presence.touch(connection.userId, connection.sessionId, now)
        reportPresence(connection.userId, true)
    }

    override fun sweepOccupancy(): Int {
        val changed = occupancy.sweep(clock.instant())
        changed.forEach { (roomId, count) -> publishOccupancy(roomId, count) }
        return changed.size
    }

    private fun requireInside(
        connection: ISocketConnection,
        roomId: UUID,
    ) {
        if (!hub.isInside(connection.sessionId, roomId)) throw DomainError.Forbidden("Open the room before using it.")
    }

    private fun leaveRoomBookkeeping(
        connection: ISocketConnection,
        roomId: UUID,
    ) {
        publishOccupancy(roomId, occupancy.exit(roomId, connection.userId, connection.sessionId, clock.instant()))
        typing.stop(roomId, connection.userId)
        typing.announce(roomId)
    }

    private fun publishOccupancy(
        roomId: UUID,
        count: Int,
    ) = events.publish(
        EventEnvelope.Companion.Topics.ROOM_OCCUPANCY,
        roomId.toString(),
        EventEnvelope(type = "room.occupancy", timestamp = clock.instant(), payload = RoomOccupancyPayload(roomId.toString(), count)),
    )

    /** Presence is best effort: a slow user-service must never break chat. */
    private fun reportPresence(
        userId: UUID,
        online: Boolean,
    ) {
        runCatching { users.setPresence(userId, online) }.onFailure { log.warn { "Could not report presence for $userId: ${it.message}" } }
    }
}
