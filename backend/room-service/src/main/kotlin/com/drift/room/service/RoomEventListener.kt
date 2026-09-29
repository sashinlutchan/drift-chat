package com.drift.room.service

import com.drift.room.common.EventEnvelope
import com.drift.room.common.EventEnvelopeJson
import com.drift.room.interfaces.IRoomService
import com.drift.room.models.RoomOccupancyPayload
import com.drift.room.models.UserDeletedPayload
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Kafka consumers (spec sections 13.2 and 17). Both handlers are idempotent — processing an event twice leaves
 * the same state.
 */
@Component
class RoomEventListener(
    private val rooms: IRoomService,
) {
    /** message-service reports how many users have a room open; that decides when the idle countdown runs. */
    @KafkaListener(topics = [EventEnvelope.Companion.Topics.ROOM_OCCUPANCY], groupId = "\${spring.kafka.consumer.group-id:room-service}")
    fun onOccupancy(message: String) {
        val event = EventEnvelopeJson.deserialize(message, RoomOccupancyPayload::class.java)
        rooms.onOccupancy(UUID.fromString(event.payload.roomId), event.payload.count)
    }

    @KafkaListener(topics = [EventEnvelope.Companion.Topics.USER_DELETED], groupId = "\${spring.kafka.consumer.group-id:room-service}")
    fun onUserDeleted(message: String) {
        val event = EventEnvelopeJson.deserialize(message, UserDeletedPayload::class.java)
        rooms.onUserDeleted(UUID.fromString(event.payload.userId))
        log.info { "user.deleted ${event.payload.userId}: memberships and pins removed" }
    }
}
