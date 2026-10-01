package com.drift.message.service

import com.drift.message.TestConnection
import com.drift.message.dto.SenderDto
import com.drift.message.dto.TypingUpdateDto
import com.drift.message.interfaces.IChatService
import com.drift.message.interfaces.IMessageRenderer
import com.drift.message.interfaces.ITypingStore
import com.drift.message.socket.ConnectionHub
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class TypingAndSweepTest {
    private val now = Instant.parse("2026-06-01T12:00:00Z")
    private val hub = ConnectionHub()
    private val typing = mockk<ITypingStore>()
    private val renderer = mockk<IMessageRenderer>()
    private val broadcaster = TypingBroadcaster(typing, renderer, hub, Clock.fixed(now, ZoneOffset.UTC))
    private val room = UUID.randomUUID()

    @Test
    fun `typing updates list who is typing and reach only sockets inside the room`() {
        val a = TestConnection().also(hub::register)
        val outside = TestConnection().also(hub::register)
        hub.enter(a.sessionId, room)
        val sipho = UUID.randomUUID()
        every { typing.active(room, now) } returns listOf(sipho)
        every { renderer.senders(listOf(sipho)) } returns listOf(SenderDto(sipho, "Sipho", "sipho", null, false))

        broadcaster.broadcast(room)

        val (event, payload) = a.sent.single()
        assertThat(event).isEqualTo("typing:update")
        assertThat((payload as TypingUpdateDto).users.map { it.name }).containsExactly("Sipho")
        assertThat(outside.sent).isEmpty()
    }

    @Test
    fun `an empty update tells clients everyone stopped typing`() {
        val a = TestConnection().also(hub::register)
        hub.enter(a.sessionId, room)
        every { typing.active(room, now) } returns emptyList()
        every { renderer.senders(emptyList()) } returns emptyList()

        broadcaster.broadcast(room)

        assertThat((a.sent.single().second as TypingUpdateDto).users).isEmpty()
    }

    @Test
    fun `a lookup failure is logged and swallowed, it never kills the pub-sub listener`() {
        every { typing.active(any(), any()) } throws IllegalStateException("valkey down")

        broadcaster.broadcast(room)
    }

    @Test
    fun `the sweeper job just asks the chat service to sweep`() {
        val chat = mockk<IChatService>()
        every { chat.sweepOccupancy() } returnsMany listOf(2, 0)
        val job = OccupancySweeper(chat)

        job.sweep()
        job.sweep()

        verify(exactly = 2) { chat.sweepOccupancy() }
    }
}
