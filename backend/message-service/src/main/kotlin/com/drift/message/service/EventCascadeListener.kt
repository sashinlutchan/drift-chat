package com.drift.message.service

import com.drift.message.common.EventEnvelope
import com.drift.message.common.EventEnvelopeJson
import com.drift.message.interfaces.IMessageService
import com.drift.message.interfaces.IUserDirectory
import com.drift.message.models.MemberEventPayload
import com.drift.message.models.RoomDeletedPayload
import com.drift.message.models.UserDeletedPayload
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.UUID

private val log = KotlinLogging.logger {}

private const val CASCADE_GROUP = "\${spring.kafka.consumer.group-id:message-service}"

/**
 * Kafka consumers that change **stored data**, so they share one consumer group (spec sections 13.3 and 17): each
 * event is handled once by the service as a whole, and not lost if every pod happens to be down (`earliest`). All
 * handlers are idempotent.
 */
@Component
class EventCascadeListener(
    private val messages: IMessageService,
    private val users: IUserDirectory,
) {
    @KafkaListener(topics = [EventEnvelope.Companion.Topics.ROOM_DELETED], groupId = CASCADE_GROUP)
    fun onRoomDeleted(json: String) {
        val roomId = UUID.fromString(EventEnvelopeJson.deserialize(json, RoomDeletedPayload::class.java).payload.roomId)
        log.info { "room.deleted $roomId: removed ${messages.deleteRoom(roomId)} messages" }
    }

    @KafkaListener(topics = [EventEnvelope.Companion.Topics.USER_DELETED], groupId = CASCADE_GROUP)
    fun onUserDeleted(json: String) {
        val userId = UUID.fromString(EventEnvelopeJson.deserialize(json, UserDeletedPayload::class.java).payload.userId)
        log.info { "user.deleted $userId: ${messages.markSenderDeleted(userId)} messages now show \"Deleted user\"" }
    }

    /** "Sipho joined the room" as an inline notice (spec section 7.2). */
    @KafkaListener(topics = [EventEnvelope.Companion.Topics.MEMBER_JOINED], groupId = CASCADE_GROUP)
    fun onMemberJoined(json: String) = notice(json, "joined")

    @KafkaListener(topics = [EventEnvelope.Companion.Topics.MEMBER_LEFT], groupId = CASCADE_GROUP)
    fun onMemberLeft(json: String) = notice(json, "left")

    private fun notice(
        json: String,
        verb: String,
    ) {
        val payload = EventEnvelopeJson.deserialize(json, MemberEventPayload::class.java).payload
        val userId = UUID.fromString(payload.userId)
        // A user who was deleted has no name left to show, so there is no notice
        val name = users.profiles(listOf(userId))[userId]?.name ?: return
        messages.sendSystem(UUID.fromString(payload.roomId), userId, "$name $verb the room")
    }
}
