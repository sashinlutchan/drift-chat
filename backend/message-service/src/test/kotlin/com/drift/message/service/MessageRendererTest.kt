package com.drift.message.service

import com.drift.message.exceptions.DomainError
import com.drift.message.interfaces.IUserDirectory
import com.drift.message.models.Message
import com.drift.message.models.MessageType
import com.drift.message.models.UserProfile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class MessageRendererTest {
    private class MovableClock(
        var now: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }

    private val clock = MovableClock(Instant.parse("2026-06-01T12:00:00Z"))
    private val users = mockk<IUserDirectory>()
    private val renderer = MessageRenderer(users, clock)
    private val room = UUID.randomUUID()
    private val sipho = UUID.randomUUID()

    private fun message(
        sender: UUID?,
        deleted: Boolean = false,
        type: MessageType = MessageType.TEXT,
    ) = Message(UUID.randomUUID(), room, sender, deleted, type, "hi", null, clock.now)

    private fun profile(id: UUID = sipho) = UserProfile(id, "sipho", "Sipho", UUID(0, 5), true)

    @Test
    fun `messages show their sender's name, username and avatar`() {
        every { users.profiles(listOf(sipho)) } returns mapOf(sipho to profile())

        val dto = renderer.render(listOf(message(sipho))).single()

        assertThat(dto.sender?.name).isEqualTo("Sipho")
        assertThat(dto.sender?.username).isEqualTo("sipho")
        assertThat(dto.sender?.avatarFileId).isEqualTo(UUID(0, 5))
        assertThat(dto.sender?.deleted).isFalse()
        assertThat(dto.type).isEqualTo("text")
    }

    @Test
    fun `messages from deleted users show Deleted user`() {
        val dto = renderer.render(listOf(message(null, deleted = true))).single()

        assertThat(dto.sender?.name).isEqualTo("Deleted user")
        assertThat(dto.sender?.deleted).isTrue()
        assertThat(dto.sender?.avatarFileId).isNull()
    }

    @Test
    fun `a sender who no longer exists in user-service also shows Deleted user`() {
        every { users.profiles(any()) } returns emptyMap()

        assertThat(
            renderer
                .render(listOf(message(sipho)))
                .single()
                .sender
                ?.name,
        ).isEqualTo("Deleted user")
    }

    @Test
    fun `system messages without a sender have no sender`() {
        assertThat(renderer.render(listOf(message(null, type = MessageType.SYSTEM))).single().sender).isNull()
    }

    @Test
    fun `profiles are cached for a minute, then fetched again`() {
        every { users.profiles(any()) } returns mapOf(sipho to profile())

        renderer.render(listOf(message(sipho)))
        renderer.render(listOf(message(sipho)))
        verify(exactly = 1) { users.profiles(any()) }

        clock.now = clock.now.plus(Duration.ofSeconds(61))
        renderer.render(listOf(message(sipho)))
        verify(exactly = 2) { users.profiles(any()) }
    }

    @Test
    fun `an update or deletion event drops the cached profile`() {
        every { users.profiles(any()) } returns mapOf(sipho to profile())
        renderer.render(listOf(message(sipho)))

        renderer.invalidate(sipho)
        renderer.render(listOf(message(sipho)))

        verify(exactly = 2) { users.profiles(any()) }
    }

    @Test
    fun `one lookup covers many messages from the same and different senders`() {
        val thandi = UUID.randomUUID()
        every { users.profiles(any()) } returns mapOf(sipho to profile(), thandi to profile(thandi).copy(name = "Thandi"))

        val dtos = renderer.render(listOf(message(sipho), message(thandi), message(sipho)))

        assertThat(dtos.map { it.sender?.name }).containsExactly("Sipho", "Thandi", "Sipho")
        verify(exactly = 1) { users.profiles(any()) }
    }

    @Test
    fun `senders lists only users that exist`() {
        val ghost = UUID.randomUUID()
        every { users.profiles(any()) } returns mapOf(sipho to profile())

        assertThat(renderer.senders(listOf(sipho, ghost)).map { it.name }).containsExactly("Sipho")
    }

    @Test
    fun `user-service errors propagate so a caller can decide`() {
        every { users.profiles(any()) } throws DomainError.DependencyUnavailable("down")

        assertThatThrownBy { renderer.render(listOf(message(sipho))) }.isInstanceOf(DomainError.DependencyUnavailable::class.java)
    }
}
