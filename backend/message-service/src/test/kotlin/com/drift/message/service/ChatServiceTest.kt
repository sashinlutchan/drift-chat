package com.drift.message.service

import com.drift.message.TestConnection
import com.drift.message.common.EventEnvelope
import com.drift.message.exceptions.DomainError
import com.drift.message.interfaces.IEventPublisher
import com.drift.message.interfaces.IMessageService
import com.drift.message.interfaces.IOccupancyStore
import com.drift.message.interfaces.IRoomMembership
import com.drift.message.interfaces.ISocketPresence
import com.drift.message.interfaces.ITypingStore
import com.drift.message.interfaces.IUserDirectory
import com.drift.message.models.Message
import com.drift.message.models.MessageType
import com.drift.message.models.RoomAccess
import com.drift.message.models.RoomOccupancyPayload
import com.drift.message.socket.ConnectionHub
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ChatServiceTest {
    private val now = Instant.parse("2026-06-01T12:00:00Z")
    private val hub = ConnectionHub()
    private val rooms = mockk<IRoomMembership>()
    private val messages = mockk<IMessageService>()
    private val occupancy = mockk<IOccupancyStore>()
    private val presence = mockk<ISocketPresence>()
    private val typing = mockk<ITypingStore>(relaxed = true)
    private val users = mockk<IUserDirectory>(relaxed = true)
    private val events = mockk<IEventPublisher>(relaxed = true)
    private val chat = ChatService(hub, rooms, messages, occupancy, presence, typing, users, events, Clock.fixed(now, ZoneOffset.UTC))

    private val room = UUID.randomUUID()
    private val socket = TestConnection()

    private fun connect(c: TestConnection = socket) {
        every { presence.connect(c.userId, c.sessionId, now) } returns true
        chat.connected(c)
    }

    private fun occupancyEvents(): List<Int> {
        val captured = mutableListOf<EventEnvelope<*>>()
        verify(atLeast = 0) { events.publish("room.occupancy", room.toString(), capture(captured)) }
        return captured.map { (it.payload as RoomOccupancyPayload).count }
    }

    @Test
    fun `the first socket of a user marks them online, later ones do not`() {
        every { presence.connect(any(), any(), any()) } returnsMany listOf(true, false)
        val second = TestConnection(socket.userId)

        chat.connected(socket)
        chat.connected(second)

        verify(exactly = 1) { users.setPresence(socket.userId, true) }
    }

    @Test
    fun `the last socket closing marks the user offline and every open room is left`() {
        connect()
        every { rooms.access(room, socket.userId) } returns RoomAccess.MEMBER
        every { occupancy.enter(room, socket.userId, socket.sessionId, now) } returns 1
        every { occupancy.exit(room, socket.userId, socket.sessionId, now) } returns 0
        every { presence.disconnect(socket.userId, socket.sessionId, now) } returns true
        chat.enterRoom(socket, room)

        chat.disconnected(socket)

        verify { users.setPresence(socket.userId, false) }
        assertThat(occupancyEvents()).containsExactly(1, 0)
        assertThat(hub.find(socket.sessionId)).isNull()
    }

    @Test
    fun `closing one of two tabs keeps the user online`() {
        connect()
        every { presence.disconnect(any(), any(), any()) } returns false

        chat.disconnected(socket)

        verify(exactly = 0) { users.setPresence(socket.userId, false) }
    }

    @Test
    fun `disconnecting twice only cleans up once`() {
        connect()
        every { presence.disconnect(any(), any(), any()) } returns true

        chat.disconnected(socket)
        chat.disconnected(socket)

        verify(exactly = 1) { presence.disconnect(any(), any(), any()) }
    }

    @Test
    fun `an unavailable user-service never breaks connecting`() {
        every { presence.connect(any(), any(), any()) } returns true
        every { users.setPresence(any(), any()) } throws DomainError.DependencyUnavailable("down")

        chat.connected(socket)

        assertThat(hub.find(socket.sessionId)).isNotNull()
    }

    @Test
    fun `members can enter a room and the occupancy is published`() {
        connect()
        every { rooms.access(room, socket.userId) } returns RoomAccess.MEMBER
        every { occupancy.enter(room, socket.userId, socket.sessionId, now) } returns 2

        chat.enterRoom(socket, room)

        assertThat(hub.isInside(socket.sessionId, room)).isTrue()
        assertThat(occupancyEvents()).containsExactly(2)
    }

    @Test
    fun `entering the same room twice on one socket is quiet`() {
        connect()
        every { rooms.access(room, socket.userId) } returns RoomAccess.MEMBER
        every { occupancy.enter(any(), any(), any(), any()) } returns 1

        chat.enterRoom(socket, room)
        chat.enterRoom(socket, room)

        verify(exactly = 1) { occupancy.enter(any(), any(), any(), any()) }
    }

    @Test
    fun `non members and deleted rooms cannot be entered`() {
        connect()
        every { rooms.access(room, socket.userId) } returns RoomAccess.NOT_MEMBER
        assertThatThrownBy { chat.enterRoom(socket, room) }.isInstanceOf(DomainError.Forbidden::class.java)

        every { rooms.access(room, socket.userId) } returns RoomAccess.NO_SUCH_ROOM
        assertThatThrownBy { chat.enterRoom(socket, room) }.isInstanceOf(DomainError.NotFound::class.java)

        assertThat(hub.isInside(socket.sessionId, room)).isFalse()
        verify(exactly = 0) { occupancy.enter(any(), any(), any(), any()) }
    }

    @Test
    fun `exiting a room stops delivery and reports the new occupancy`() {
        connect()
        every { rooms.access(room, socket.userId) } returns RoomAccess.MEMBER
        every { occupancy.enter(any(), any(), any(), any()) } returns 1
        every { occupancy.exit(room, socket.userId, socket.sessionId, now) } returns 0
        chat.enterRoom(socket, room)

        chat.exitRoom(socket, room)
        chat.exitRoom(socket, room)

        assertThat(hub.isInside(socket.sessionId, room)).isFalse()
        assertThat(occupancyEvents()).containsExactly(1, 0)
        verify { typing.stop(room, socket.userId) }
    }

    private fun enter() {
        connect()
        every { rooms.access(room, socket.userId) } returns RoomAccess.MEMBER
        every { occupancy.enter(any(), any(), any(), any()) } returns 1
        chat.enterRoom(socket, room)
    }

    @Test
    fun `sending returns the ack mapping clientId to the message id and ends typing`() {
        enter()
        val id = UUID.randomUUID()
        every { messages.sendText(room, socket.userId, "hello", "c1") } returns
            Message(id, room, socket.userId, false, MessageType.TEXT, "hello", null, now)

        val ack = chat.sendMessage(socket, room, "hello", "c1")

        assertThat(ack).containsEntry("clientId", "c1").containsEntry("id", id.toString()).containsEntry("createdAt", now.toString())
        verify { typing.stop(room, socket.userId) }
        verify { typing.announce(room) }
    }

    @Test
    fun `you cannot send to or type in a room you have not opened`() {
        connect()

        assertThatThrownBy { chat.sendMessage(socket, room, "hi", "c1") }.isInstanceOf(DomainError.Forbidden::class.java)
        assertThatThrownBy { chat.typing(socket, room, true) }.isInstanceOf(DomainError.Forbidden::class.java)
        verify(exactly = 0) { messages.sendText(any(), any(), any(), any()) }
    }

    @Test
    fun `typing start and stop update the store and tell every pod`() {
        enter()

        chat.typing(socket, room, true)
        chat.typing(socket, room, false)

        verify { typing.start(room, socket.userId, now) }
        verify(atLeast = 2) { typing.announce(room) }
    }

    @Test
    fun `a heartbeat refreshes occupancy, socket presence and the online flag`() {
        enter()
        every { occupancy.touch(any(), any(), any(), any()) } just runs
        every { presence.touch(any(), any(), any()) } just runs

        chat.heartbeat(socket)

        verify { occupancy.touch(room, socket.userId, socket.sessionId, now) }
        verify { presence.touch(socket.userId, socket.sessionId, now) }
        verify(atLeast = 1) { users.setPresence(socket.userId, true) }
    }

    @Test
    fun `the sweep announces rooms that emptied because their sockets vanished`() {
        every { occupancy.sweep(now) } returns mapOf(room to 0)

        assertThat(chat.sweepOccupancy()).isEqualTo(1)
        assertThat(occupancyEvents()).containsExactly(0)
    }

    @Test
    fun `a sweep with nothing stale announces nothing`() {
        every { occupancy.sweep(now) } returns emptyMap()
        val payload = slot<EventEnvelope<*>>()

        assertThat(chat.sweepOccupancy()).isZero()
        verify(exactly = 0) { events.publish(any(), any(), capture(payload)) }
    }
}
