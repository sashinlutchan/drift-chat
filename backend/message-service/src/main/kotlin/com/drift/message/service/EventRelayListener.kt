package com.drift.message.service

import com.drift.message.common.EventEnvelope
import com.drift.message.common.EventEnvelopeJson
import com.drift.message.interfaces.IConnectionHub
import com.drift.message.interfaces.IMessageRenderer
import com.drift.message.interfaces.IMessageService
import com.drift.message.interfaces.IOccupancyStore
import com.drift.message.models.MemberEventPayload
import com.drift.message.models.MessageCreatedPayload
import com.drift.message.models.MessageType
import com.drift.message.models.PresenceChangedPayload
import com.drift.message.models.RoomDeletedPayload
import com.drift.message.models.RoomEventPayload
import com.drift.message.models.StoredMessage
import com.drift.message.models.UserDeletedPayload
import com.drift.message.models.UserUpdatedPayload
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

private const val RELAY_GROUP = "#{'message-service-relay-' + @podIdentity.id}"
private const val LATEST = "auto.offset.reset=latest"

/**
 * Turns Kafka events into Socket.IO events for the sockets on **this pod** (spec sections 13.3, 15 and 17). Every pod
 * reads under its own consumer group, so an event reaches every pod and each one serves its own sockets. That is what
 * makes fan-out work across replicas. Old events are not replayed to a fresh pod (`latest`).
 */
@Component
class EventRelayListener(
    private val hub: IConnectionHub,
    private val messages: IMessageService,
    private val renderer: IMessageRenderer,
    private val occupancy: IOccupancyStore,
    private val meters: MeterRegistry,
    private val clock: Clock,
) {
    @KafkaListener(topics = [EventEnvelope.Companion.Topics.MESSAGE_CREATED], groupId = RELAY_GROUP, properties = [LATEST])
    fun onMessageCreated(json: String) {
        val p = EventEnvelopeJson.deserialize(json, MessageCreatedPayload::class.java).payload
        val decoder = Base64.getDecoder()
        val stored =
            StoredMessage(
                id = UUID.fromString(p.id),
                roomId = UUID.fromString(p.roomId),
                senderId = p.senderId?.let(UUID::fromString),
                senderDeleted = false,
                type = MessageType.fromDb(p.type),
                ciphertext = decoder.decode(p.ciphertext),
                nonce = decoder.decode(p.nonce),
                keyVersion = p.keyVersion,
                fileId = p.fileId?.let(UUID::fromString),
                createdAt = Instant.parse(p.createdAt),
            )
        val dto = renderer.render(listOf(messages.decrypt(stored))).first()
        hub.sendToRoom(stored.roomId, "message:new", dto)
        meters.timer("drift.messages.fanout.latency").record(Duration.between(stored.createdAt, clock.instant()))
    }

    @KafkaListener(topics = [EventEnvelope.Companion.Topics.PRESENCE_CHANGED], groupId = RELAY_GROUP, properties = [LATEST])
    fun onPresenceChanged(json: String) {
        hub.sendToAll("presence:update", EventEnvelopeJson.deserialize(json, PresenceChangedPayload::class.java).payload)
    }

    @KafkaListener(topics = [EventEnvelope.Companion.Topics.USER_UPDATED], groupId = RELAY_GROUP, properties = [LATEST])
    fun onUserUpdated(json: String) {
        val payload = EventEnvelopeJson.deserialize(json, UserUpdatedPayload::class.java).payload
        renderer.invalidate(UUID.fromString(payload.userId))
        hub.sendToAll("user:updated", payload)
    }

    @KafkaListener(topics = [EventEnvelope.Companion.Topics.USER_DELETED], groupId = RELAY_GROUP, properties = [LATEST])
    fun onUserDeleted(json: String) {
        val payload = EventEnvelopeJson.deserialize(json, UserDeletedPayload::class.java).payload
        renderer.invalidate(UUID.fromString(payload.userId))
        hub.sendToAll("user:deleted", payload)
    }

    @KafkaListener(topics = [EventEnvelope.Companion.Topics.ROOM_CREATED], groupId = RELAY_GROUP, properties = [LATEST])
    fun onRoomCreated(json: String) {
        hub.sendToAll("room:created", EventEnvelopeJson.deserialize(json, RoomEventPayload::class.java).payload)
    }

    @KafkaListener(topics = [EventEnvelope.Companion.Topics.ROOM_UPDATED], groupId = RELAY_GROUP, properties = [LATEST])
    fun onRoomUpdated(json: String) {
        hub.sendToAll("room:updated", EventEnvelopeJson.deserialize(json, RoomEventPayload::class.java).payload)
    }

    /** The room is gone: everyone hears about it and sockets that had it open are taken out of it. */
    @KafkaListener(topics = [EventEnvelope.Companion.Topics.ROOM_DELETED], groupId = RELAY_GROUP, properties = [LATEST])
    fun onRoomDeleted(json: String) {
        val payload = EventEnvelopeJson.deserialize(json, RoomDeletedPayload::class.java).payload
        val roomId = UUID.fromString(payload.roomId)
        hub.sendToAll("room:deleted", payload)
        hub.clearRoom(roomId).forEach { occupancy.exit(roomId, it.userId, it.sessionId, clock.instant()) }
    }

    @KafkaListener(topics = [EventEnvelope.Companion.Topics.MEMBER_JOINED], groupId = RELAY_GROUP, properties = [LATEST])
    fun onMemberJoined(json: String) {
        val payload = EventEnvelopeJson.deserialize(json, MemberEventPayload::class.java).payload
        hub.sendToRoom(UUID.fromString(payload.roomId), "member:joined", payload)
    }

    /** Somebody left the room: the others are told, and the leaver's sockets stop receiving its messages. */
    @KafkaListener(topics = [EventEnvelope.Companion.Topics.MEMBER_LEFT], groupId = RELAY_GROUP, properties = [LATEST])
    fun onMemberLeft(json: String) {
        val payload = EventEnvelopeJson.deserialize(json, MemberEventPayload::class.java).payload
        val roomId = UUID.fromString(payload.roomId)
        val userId = UUID.fromString(payload.userId)
        hub.sendToRoom(roomId, "member:left", payload)
        hub.evictUser(roomId, userId).forEach { occupancy.exit(roomId, it.userId, it.sessionId, clock.instant()) }
    }
}
