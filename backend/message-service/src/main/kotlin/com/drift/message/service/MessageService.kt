package com.drift.message.service

import com.drift.message.common.EventEnvelope
import com.drift.message.config.AppProperties
import com.drift.message.exceptions.DomainError
import com.drift.message.interfaces.IDedupStore
import com.drift.message.interfaces.IEventPublisher
import com.drift.message.interfaces.IMessageCipher
import com.drift.message.interfaces.IMessageService
import com.drift.message.interfaces.IRoomMembership
import com.drift.message.models.Message
import com.drift.message.models.MessageCreatedPayload
import com.drift.message.models.MessagePage
import com.drift.message.models.MessageType
import com.drift.message.models.RoomAccess
import com.drift.message.models.StoredMessage
import com.drift.message.repository.IMessageRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.UUID

@Service
class MessageService(
    private val repository: IMessageRepository,
    private val cipher: IMessageCipher,
    private val events: IEventPublisher,
    private val dedup: IDedupStore,
    private val rooms: IRoomMembership,
    private val properties: AppProperties,
    private val meters: MeterRegistry,
    private val clock: Clock,
) : IMessageService {
    override fun sendText(
        roomId: UUID,
        senderId: UUID,
        text: String,
        clientId: String,
    ): Message {
        if (text.isBlank()) throw DomainError.ValidationFailed("A message can't be empty.")
        if (text.length > properties.maxMessageLength) {
            throw DomainError.ValidationFailed("A message can be at most ${properties.maxMessageLength} characters.")
        }
        if (clientId.isBlank() || clientId.length > MAX_CLIENT_ID) throw DomainError.ValidationFailed("clientId is required.")

        val id = UUID.randomUUID()
        val earlier = dedup.claim(senderId, clientId, id, DEDUP_TTL)
        if (earlier != null) {
            return repository.findById(earlier)?.let(::decrypt)
                ?: Message(earlier, roomId, senderId, false, MessageType.TEXT, text, null, clock.instant())
        }
        return store(id, roomId, senderId, MessageType.TEXT, text, null)
    }

    override fun sendMedia(
        roomId: UUID,
        senderId: UUID,
        fileId: UUID,
        fileName: String,
        type: MessageType,
    ): Message = store(UUID.randomUUID(), roomId, senderId, type, fileName, fileId)

    override fun sendSystem(
        roomId: UUID,
        subjectId: UUID?,
        text: String,
    ): Message = store(UUID.randomUUID(), roomId, subjectId, MessageType.SYSTEM, text, null)

    override fun history(
        userId: UUID,
        roomId: UUID,
        before: UUID?,
        limit: Int?,
    ): MessagePage {
        when (rooms.access(roomId, userId)) {
            RoomAccess.NO_SUCH_ROOM -> throw DomainError.NotFound("This room no longer exists.")
            RoomAccess.NOT_MEMBER -> throw DomainError.Forbidden("Join this room to read its messages.")
            RoomAccess.MEMBER -> Unit
        }
        val size = (limit ?: properties.historyPageSize).coerceIn(1, MAX_PAGE)
        val rows = repository.page(roomId, before, size)
        return MessagePage(rows.map(::decrypt), if (rows.size == size) rows.last().id else null)
    }

    override fun decrypt(stored: StoredMessage): Message =
        Message(
            id = stored.id,
            roomId = stored.roomId,
            senderId = stored.senderId,
            senderDeleted = stored.senderDeleted,
            type = stored.type,
            text = String(cipher.decrypt(stored.ciphertext, stored.nonce, stored.keyVersion), Charsets.UTF_8),
            fileId = stored.fileId,
            createdAt = stored.createdAt,
        )

    override fun deleteRoom(roomId: UUID): Int = repository.deleteByRoom(roomId)

    override fun markSenderDeleted(userId: UUID): Int = repository.markSenderDeleted(userId)

    private fun store(
        id: UUID,
        roomId: UUID,
        senderId: UUID?,
        type: MessageType,
        text: String,
        fileId: UUID?,
    ): Message {
        val encrypted = cipher.encrypt(text.toByteArray(Charsets.UTF_8))
        val saved =
            repository.save(
                StoredMessage(
                    id,
                    roomId,
                    senderId,
                    false,
                    type,
                    encrypted.ciphertext,
                    encrypted.nonce,
                    encrypted.keyVersion,
                    fileId,
                    clock.instant(),
                ),
            )
        val encoder = Base64.getEncoder()
        events.publish(
            EventEnvelope.Companion.Topics.MESSAGE_CREATED,
            roomId.toString(),
            EventEnvelope(
                type = "message.created",
                timestamp = clock.instant(),
                payload =
                    MessageCreatedPayload(
                        id = saved.id.toString(),
                        roomId = roomId.toString(),
                        senderId = senderId?.toString(),
                        type = type.dbValue,
                        ciphertext = encoder.encodeToString(saved.ciphertext),
                        nonce = encoder.encodeToString(saved.nonce),
                        keyVersion = saved.keyVersion,
                        fileId = fileId?.toString(),
                        createdAt = saved.createdAt.toString(),
                    ),
            ),
        )
        meters.counter("drift.messages.sent", "type", type.dbValue).increment()
        return decrypt(saved)
    }

    private companion object {
        const val MAX_CLIENT_ID = 64
        const val MAX_PAGE = 100
        val DEDUP_TTL: Duration = Duration.ofMinutes(10)
    }
}
