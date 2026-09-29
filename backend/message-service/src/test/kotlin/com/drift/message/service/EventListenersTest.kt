package com.drift.message.service

import com.drift.message.TestConnection
import com.drift.message.TestSupport
import com.drift.message.common.EventEnvelope
import com.drift.message.common.EventEnvelopeJson
import com.drift.message.dto.MessageDto
import com.drift.message.interfaces.IEventPublisher
import com.drift.message.interfaces.IOccupancyStore
import com.drift.message.interfaces.IUserDirectory
import com.drift.message.models.MemberEventPayload
import com.drift.message.models.MessageCreatedPayload
import com.drift.message.models.MessageType
import com.drift.message.models.PresenceChangedPayload
import com.drift.message.models.RoomDeletedPayload
import com.drift.message.models.RoomEventPayload
import com.drift.message.models.UserDeletedPayload
import com.drift.message.models.UserProfile
import com.drift.message.models.UserUpdatedPayload
import com.drift.message.repository.IMessageRepository
import com.drift.message.socket.ConnectionHub
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

class EventListenersTest {
    private val now = Instant.parse("2026-06-01T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val hub = ConnectionHub()
    private val users = mockk<IUserDirectory>()
    private val renderer = MessageRenderer(users, clock)
    private val repository = mockk<IMessageRepository>()
    private val occupancy = mockk<IOccupancyStore>()
    private val events = mockk<IEventPublisher>(relaxed = true)
    private val messages =
        MessageService(
            repository,
            TestSupport.cipher,
            events,
            mockk(relaxed = true),
            mockk(relaxed = true),
            TestSupport.properties(),
            SimpleMeterRegistry(),
            clock,
        )
    private val relay = EventRelayListener(hub, messages, renderer, occupancy, SimpleMeterRegistry(), clock)

    private val room = UUID.randomUUID()
    private val sipho = UUID.randomUUID()
    private val inRoom = TestConnection()
    private val outside = TestConnection()

    init {
        hub.register(inRoom)
        hub.register(outside)
        hub.enter(inRoom.sessionId, room)
        every { occupancy.exit(any(), any(), any(), any()) } returns 0
        every { users.profiles(any()) } returns mapOf(sipho to UserProfile(sipho, "sipho", "Sipho", null, true))
    }

    private fun json(
        type: String,
        payload: Any,
    ) = EventEnvelopeJson.serialize(EventEnvelope(type = type, timestamp = now, payload = payload))

    // ── relay: per-pod fan-out ──────────────────────────────────────────────

    @Test
    fun `a new message is decrypted and pushed only to sockets in that room`() {
        val enc = TestSupport.cipher.encrypt("hello there".toByteArray())
        val b64 = Base64.getEncoder()
        val payload =
            MessageCreatedPayload(
                UUID.randomUUID().toString(),
                room.toString(),
                sipho.toString(),
                "text",
                b64.encodeToString(enc.ciphertext),
                b64.encodeToString(enc.nonce),
                enc.keyVersion,
                null,
                now.toString(),
            )

        relay.onMessageCreated(json("message.created", payload))

        val (event, dto) = inRoom.sent.single()
        assertThat(event).isEqualTo("message:new")
        assertThat((dto as MessageDto).text).isEqualTo("hello there")
        assertThat(dto.sender?.name).isEqualTo("Sipho")
        assertThat(dto.type).isEqualTo("text")
        assertThat(outside.sent).isEmpty()
    }

    @Test
    fun `media messages carry their file id`() {
        val enc = TestSupport.cipher.encrypt("clip.mp4".toByteArray())
        val b64 = Base64.getEncoder()
        val fileId = UUID.randomUUID()
        val payload =
            MessageCreatedPayload(
                UUID.randomUUID().toString(),
                room.toString(),
                sipho.toString(),
                "video",
                b64.encodeToString(enc.ciphertext),
                b64.encodeToString(enc.nonce),
                1,
                fileId.toString(),
                now.toString(),
            )

        relay.onMessageCreated(json("message.created", payload))

        val dto = inRoom.sent.single().second as MessageDto
        assertThat(dto.fileId).isEqualTo(fileId)
        assertThat(dto.type).isEqualTo("video")
    }

    @Test
    fun `presence, room and user events go to every socket on this pod`() {
        relay.onPresenceChanged(json("presence.changed", PresenceChangedPayload(sipho.toString(), true)))
        relay.onRoomCreated(json("room.created", RoomEventPayload(room.toString(), "R", "r-1", 1)))
        relay.onRoomUpdated(json("room.updated", RoomEventPayload(room.toString(), "R", "r-1", 2)))
        relay.onUserUpdated(json("user.updated", UserUpdatedPayload(sipho.toString(), "sipho", "Sipho", null)))
        relay.onUserDeleted(json("user.deleted", UserDeletedPayload(sipho.toString())))

        val expected = listOf("presence:update", "room:created", "room:updated", "user:updated", "user:deleted")
        assertThat(inRoom.events()).containsExactlyElementsOf(expected)
        assertThat(outside.events()).containsExactlyElementsOf(expected)
    }

    @Test
    fun `a deleted room is announced and sockets that had it open are taken out of it`() {
        relay.onRoomDeleted(json("room.deleted", RoomDeletedPayload(room.toString())))

        assertThat(inRoom.events()).containsExactly("room:deleted")
        assertThat(outside.events()).containsExactly("room:deleted")
        assertThat(hub.isInside(inRoom.sessionId, room)).isFalse()
        verify { occupancy.exit(room, inRoom.userId, inRoom.sessionId, now) }
    }

    @Test
    fun `member joined reaches the room, member left also evicts the leaver's sockets`() {
        relay.onMemberJoined(json("member.joined", MemberEventPayload(room.toString(), sipho.toString())))
        assertThat(inRoom.events()).containsExactly("member:joined")
        assertThat(outside.events()).isEmpty()

        relay.onMemberLeft(json("member.left", MemberEventPayload(room.toString(), inRoom.userId.toString())))
        assertThat(inRoom.events()).containsExactly("member:joined", "member:left")
        assertThat(hub.isInside(inRoom.sessionId, room)).isFalse()
    }

    // ── cascade: shared group ───────────────────────────────────────────────

    private val cascade = EventCascadeListener(messages, users)

    @Test
    fun `room deleted removes its messages, user deleted marks their messages`() {
        every { repository.deleteByRoom(room) } returns 7
        every { repository.markSenderDeleted(sipho) } returns 3

        cascade.onRoomDeleted(json("room.deleted", RoomDeletedPayload(room.toString())))
        cascade.onUserDeleted(json("user.deleted", UserDeletedPayload(sipho.toString())))

        verify { repository.deleteByRoom(room) }
        verify { repository.markSenderDeleted(sipho) }
    }

    @Test
    fun `joining and leaving become inline notices`() {
        val saved = mutableListOf<com.drift.message.models.StoredMessage>()
        every { repository.save(capture(saved)) } answers { firstArg() }

        cascade.onMemberJoined(json("member.joined", MemberEventPayload(room.toString(), sipho.toString())))
        cascade.onMemberLeft(json("member.left", MemberEventPayload(room.toString(), sipho.toString())))

        val texts = saved.map { String(TestSupport.cipher.decrypt(it.ciphertext, it.nonce, it.keyVersion)) }
        assertThat(texts).containsExactly("Sipho joined the room", "Sipho left the room")
        assertThat(saved).allMatch { it.type == MessageType.SYSTEM && it.roomId == room }
    }

    @Test
    fun `a deleted user leaving has no name so no notice is made`() {
        every { users.profiles(any()) } returns emptyMap()

        cascade.onMemberLeft(json("member.left", MemberEventPayload(room.toString(), sipho.toString())))

        verify(exactly = 0) { repository.save(any()) }
    }
}
