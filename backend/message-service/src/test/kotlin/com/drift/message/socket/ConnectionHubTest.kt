package com.drift.message.socket

import com.drift.message.TestConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ConnectionHubTest {
    private val hub = ConnectionHub()
    private val room = UUID.randomUUID()
    private val other = UUID.randomUUID()

    private fun connect(user: UUID = UUID.randomUUID()): TestConnection = TestConnection(user).also(hub::register)

    @Test
    fun `entering is idempotent per socket and broadcasts reach only that room`() {
        val a = connect()
        val b = connect()
        val c = connect()
        assertThat(hub.enter(a.sessionId, room)).isTrue()
        assertThat(hub.enter(a.sessionId, room)).isFalse()
        hub.enter(b.sessionId, room)
        hub.enter(c.sessionId, other)

        hub.sendToRoom(room, "message:new", "hi")

        assertThat(a.events()).containsExactly("message:new")
        assertThat(b.events()).containsExactly("message:new")
        assertThat(c.events()).isEmpty()
    }

    @Test
    fun `it counts the rooms that have someone inside`() {
        val a = connect()
        val b = connect()
        assertThat(hub.occupiedRoomCount()).isZero()

        hub.enter(a.sessionId, room)
        hub.enter(b.sessionId, room)
        hub.enter(b.sessionId, other)
        assertThat(hub.occupiedRoomCount()).isEqualTo(2)

        hub.unregister(b.sessionId)
        assertThat(hub.occupiedRoomCount()).isEqualTo(1)
    }

    @Test
    fun `sending to all reaches every socket`() {
        val a = connect()
        val b = connect()

        hub.sendToAll("presence:update", "x")

        assertThat(a.events()).containsExactly("presence:update")
        assertThat(b.events()).containsExactly("presence:update")
    }

    @Test
    fun `a broken socket does not stop the broadcast reaching the others`() {
        val broken = connect().apply { failOnSend = true }
        val fine = connect()
        hub.enter(broken.sessionId, room)
        hub.enter(fine.sessionId, room)

        hub.sendToRoom(room, "message:new", "hi")

        assertThat(fine.events()).containsExactly("message:new")
    }

    @Test
    fun `exit and unregister stop delivery and report the rooms that were open`() {
        val a = connect()
        hub.enter(a.sessionId, room)
        hub.enter(a.sessionId, other)

        assertThat(hub.exit(a.sessionId, room)).isTrue()
        assertThat(hub.exit(a.sessionId, room)).isFalse()
        assertThat(hub.isInside(a.sessionId, room)).isFalse()
        hub.sendToRoom(room, "x", "y")
        assertThat(a.events()).isEmpty()

        assertThat(hub.unregister(a.sessionId)).containsExactly(other)
        assertThat(hub.find(a.sessionId)).isNull()
        assertThat(hub.connectionCount()).isZero()
    }

    @Test
    fun `entering with an unknown socket does nothing`() {
        assertThat(hub.enter("ghost", room)).isFalse()
        assertThat(hub.roomsOf("ghost")).isEmpty()
        assertThat(hub.unregister("ghost")).isEmpty()
    }

    @Test
    fun `evicting a user takes only their sockets out of the room`() {
        val user = UUID.randomUUID()
        val tab1 = connect(user)
        val tab2 = connect(user)
        val friend = connect()
        listOf(tab1, tab2, friend).forEach { hub.enter(it.sessionId, room) }

        val evicted = hub.evictUser(room, user)

        assertThat(evicted.map { it.sessionId }).containsExactlyInAnyOrder(tab1.sessionId, tab2.sessionId)
        assertThat(hub.isInside(friend.sessionId, room)).isTrue()
        assertThat(hub.isInside(tab1.sessionId, room)).isFalse()
    }

    @Test
    fun `clearing a room takes every socket out of it`() {
        val a = connect()
        val b = connect()
        hub.enter(a.sessionId, room)
        hub.enter(b.sessionId, room)

        assertThat(hub.clearRoom(room)).hasSize(2)
        assertThat(hub.roomsOf(a.sessionId)).isEmpty()
        hub.sendToRoom(room, "x", "y")
        assertThat(a.events()).isEmpty()
    }

    @Test
    fun `disconnect all closes every socket even if one fails`() {
        val a = connect()
        val b = connect()

        hub.disconnectAll()

        assertThat(a.disconnected).isTrue()
        assertThat(b.disconnected).isTrue()
    }

    @Test
    fun `many sockets entering, leaving and receiving at once leave the hub consistent`() =
        runBlocking {
            val sockets = (1..200).map { connect() }
            val rooms = (1..5).map { UUID.randomUUID() }

            sockets
                .map { socket ->
                    async(Dispatchers.Default) {
                        repeat(50) { round ->
                            val r = rooms[(round + socket.sessionId.hashCode().mod(5)) % rooms.size]
                            hub.enter(socket.sessionId, r)
                            hub.sendToRoom(r, "tick", round)
                            if (round % 2 == 0) hub.exit(socket.sessionId, r)
                        }
                    }
                }.awaitAll()

            sockets.forEach { socket ->
                hub.roomsOf(socket.sessionId).forEach { r -> assertThat(hub.isInside(socket.sessionId, r)).isTrue() }
            }
            // every remaining entry is a real one: unregistering everything leaves nothing behind
            sockets.forEach { hub.unregister(it.sessionId) }
            rooms.forEach { r ->
                hub.sendToRoom(r, "after", 1)
            }
            assertThat(hub.connectionCount()).isZero()
            assertThat(sockets.flatMap { it.events() }.count { it == "after" }).isZero()
        }
}
