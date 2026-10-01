package com.drift.message.service

import com.drift.message.TestSupport
import com.drift.message.common.EventEnvelope
import com.drift.message.exceptions.DomainError
import com.drift.message.interfaces.IDedupStore
import com.drift.message.interfaces.IEventPublisher
import com.drift.message.interfaces.IRoomMembership
import com.drift.message.models.MessageCreatedPayload
import com.drift.message.models.MessageType
import com.drift.message.models.RoomAccess
import com.drift.message.models.StoredMessage
import com.drift.message.repository.IMessageRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

class MessageServiceTest {
    private val now = Instant.parse("2026-06-01T12:00:00Z")
    private val repository = mockk<IMessageRepository>()
    private val events = mockk<IEventPublisher>(relaxed = true)
    private val dedup = mockk<IDedupStore>()
    private val rooms = mockk<IRoomMembership>()
    private val meters = SimpleMeterRegistry()
    private val service =
        MessageService(
            repository,
            TestSupport.cipher,
            events,
            dedup,
            rooms,
            TestSupport.properties(maxMessageLength = 50, pageSize = 3),
            meters,
            Clock.fixed(now, ZoneOffset.UTC),
        )

    private val room = UUID.randomUUID()
    private val me = UUID.randomUUID()
    private val saved = slot<StoredMessage>()

    private fun stubSave() {
        every { dedup.claim(any(), any(), any(), any()) } returns null
        every { repository.save(capture(saved)) } answers { firstArg() }
    }

    @Test
    fun `text is encrypted before it is saved and nothing readable reaches the database`() {
        stubSave()

        val message = service.sendText(room, me, "secret plans", "c1")

        assertThat(message.text).isEqualTo("secret plans")
        assertThat(String(saved.captured.ciphertext)).doesNotContain("secret")
        assertThat(saved.captured.nonce).hasSize(12)
        assertThat(saved.captured.keyVersion).isEqualTo(1)
        assertThat(saved.captured.type).isEqualTo(MessageType.TEXT)
        assertThat(saved.captured.senderId).isEqualTo(me)
        assertThat(saved.captured.createdAt).isEqualTo(now)
    }

    @Test
    fun `message created events carry only ciphertext`() {
        stubSave()
        val envelope = slot<EventEnvelope<MessageCreatedPayload>>()
        every { events.publish("message.created", room.toString(), capture(envelope)) } returns Unit

        service.sendText(room, me, "secret plans", "c1")

        val payload = envelope.captured.payload
        assertThat(payload.roomId).isEqualTo(room.toString())
        assertThat(payload.type).isEqualTo("text")
        assertThat(payload.toString()).doesNotContain("secret")
        val plain =
            TestSupport.cipher.decrypt(
                Base64.getDecoder().decode(payload.ciphertext),
                Base64.getDecoder().decode(payload.nonce),
                payload.keyVersion,
            )
        assertThat(String(plain)).isEqualTo("secret plans")
        assertThat(meters.counter("drift.messages.sent", "type", "text").count()).isEqualTo(1.0)
    }

    @Test
    fun `a retried clientId returns the first message instead of creating a second`() {
        val firstId = UUID.randomUUID()
        val original =
            StoredMessage(
                firstId,
                room,
                me,
                false,
                MessageType.TEXT,
                TestSupport.cipher.encrypt("hello".toByteArray()).let {
                    it.ciphertext
                },
                ByteArray(12),
                1,
                null,
                now,
            )
        val encrypted = TestSupport.cipher.encrypt("hello".toByteArray())
        every { dedup.claim(me, "c1", any(), any()) } returns firstId
        every { repository.findById(firstId) } returns original.copy(ciphertext = encrypted.ciphertext, nonce = encrypted.nonce)

        val message = service.sendText(room, me, "hello", "c1")

        assertThat(message.id).isEqualTo(firstId)
        verify(exactly = 0) { repository.save(any()) }
        verify(exactly = 0) { events.publish(any(), any(), any<EventEnvelope<*>>()) }
    }

    @Test
    fun `a retry that races the first save still gets the same id back`() {
        val firstId = UUID.randomUUID()
        every { dedup.claim(me, "c1", any(), any()) } returns firstId
        every { repository.findById(firstId) } returns null

        assertThat(service.sendText(room, me, "hello", "c1").id).isEqualTo(firstId)
    }

    @Test
    fun `empty, oversized and clientId-less messages are rejected before anything is stored`() {
        assertThatThrownBy { service.sendText(room, me, "   ", "c1") }.isInstanceOf(DomainError.ValidationFailed::class.java)
        assertThatThrownBy { service.sendText(room, me, "x".repeat(51), "c1") }.hasMessageContaining("50")
        assertThatThrownBy { service.sendText(room, me, "hi", " ") }.isInstanceOf(DomainError.ValidationFailed::class.java)
        assertThatThrownBy { service.sendText(room, me, "hi", "c".repeat(65)) }.isInstanceOf(DomainError.ValidationFailed::class.java)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `unicode text survives the round trip`() {
        stubSave()
        assertThat(service.sendText(room, me, "héllo 😀 世界", "c2").text).isEqualTo("héllo 😀 世界")
    }

    @Test
    fun `media messages keep the file id and the file name as text`() {
        every { repository.save(capture(saved)) } answers { firstArg() }
        val fileId = UUID.randomUUID()

        val message = service.sendMedia(room, me, fileId, "holiday.mp4", MessageType.VIDEO)

        assertThat(message.type).isEqualTo(MessageType.VIDEO)
        assertThat(message.fileId).isEqualTo(fileId)
        assertThat(message.text).isEqualTo("holiday.mp4")
        assertThat(saved.captured.fileId).isEqualTo(fileId)
        assertThat(String(saved.captured.ciphertext)).doesNotContain("holiday")
    }

    @Test
    fun `system notices are stored as system messages`() {
        every { repository.save(capture(saved)) } answers { firstArg() }

        val message = service.sendSystem(room, me, "Sipho joined the room")

        assertThat(message.type).isEqualTo(MessageType.SYSTEM)
        assertThat(message.text).isEqualTo("Sipho joined the room")
    }

    private fun stored(text: String) =
        TestSupport.cipher.encrypt(text.toByteArray()).let {
            StoredMessage(UUID.randomUUID(), room, me, false, MessageType.TEXT, it.ciphertext, it.nonce, it.keyVersion, null, now)
        }

    @Test
    fun `history is decrypted and a full page carries the cursor for the next one`() {
        val rows = listOf(stored("c"), stored("b"), stored("a"))
        every { rooms.access(room, me) } returns RoomAccess.MEMBER
        every { repository.page(room, null, 3) } returns rows

        val page = service.history(me, room, null, null)

        assertThat(page.messages.map { it.text }).containsExactly("c", "b", "a")
        assertThat(page.nextCursor).isEqualTo(rows.last().id)
    }

    @Test
    fun `a short page means there is nothing older`() {
        every { rooms.access(room, me) } returns RoomAccess.MEMBER
        every { repository.page(room, null, 3) } returns listOf(stored("only"))

        assertThat(service.history(me, room, null, null).nextCursor).isNull()
    }

    @Test
    fun `the page size is clamped and the before cursor is passed through`() {
        val cursor = UUID.randomUUID()
        every { rooms.access(room, me) } returns RoomAccess.MEMBER
        every { repository.page(room, cursor, 100) } returns emptyList()
        every { repository.page(room, cursor, 1) } returns emptyList()

        service.history(me, room, cursor, 5000)
        service.history(me, room, cursor, 0)

        verify { repository.page(room, cursor, 100) }
        verify { repository.page(room, cursor, 1) }
    }

    @Test
    fun `only members can read history, and a deleted room is not found`() {
        every { rooms.access(room, me) } returns RoomAccess.NOT_MEMBER
        assertThatThrownBy { service.history(me, room, null, null) }.isInstanceOf(DomainError.Forbidden::class.java)

        every { rooms.access(room, me) } returns RoomAccess.NO_SUCH_ROOM
        assertThatThrownBy { service.history(me, room, null, null) }.isInstanceOf(DomainError.NotFound::class.java)
        verify(exactly = 0) { repository.page(any(), any(), any()) }
    }

    @Test
    fun `old messages stay readable after the encryption key is rotated`() {
        val oldKey = ByteArray(32) { 7 }
        val oldCipher =
            com.drift.message.common
                .MessageCipher(mapOf(1 to oldKey), 1)
        val rotated =
            com.drift.message.common
                .MessageCipher(mapOf(1 to oldKey, 2 to ByteArray(32) { 9 }), 2)
        val enc = oldCipher.encrypt("from the old days".toByteArray())
        val svc =
            MessageService(repository, rotated, events, dedup, rooms, TestSupport.properties(), meters, Clock.fixed(now, ZoneOffset.UTC))

        val message =
            svc.decrypt(
                StoredMessage(UUID.randomUUID(), room, me, false, MessageType.TEXT, enc.ciphertext, enc.nonce, 1, null, now),
            )

        assertThat(message.text).isEqualTo("from the old days")
    }

    @Test
    fun `room and user cascades delegate to the repository`() {
        every { repository.deleteByRoom(room) } returns 4
        every { repository.markSenderDeleted(me) } returns 2

        assertThat(service.deleteRoom(room)).isEqualTo(4)
        assertThat(service.markSenderDeleted(me)).isEqualTo(2)
    }
}
