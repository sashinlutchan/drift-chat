package com.drift.file.service

import com.drift.file.common.EventEnvelope
import com.drift.file.common.EventEnvelopeJson
import com.drift.file.interfaces.IFileService
import com.drift.file.models.RoomDeletedPayload
import com.drift.file.models.UserDeletedPayload
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Kafka consumers (spec sections 13.4 and 17): a deleted room takes its files with it, a deleted user takes
 * their avatar. Handlers are idempotent — replaying an event finds nothing left to delete.
 */
@Component
class FileEventListener(
    private val files: IFileService,
) {
    @KafkaListener(topics = [EventEnvelope.Companion.Topics.ROOM_DELETED], groupId = "\${spring.kafka.consumer.group-id:file-service}")
    fun onRoomDeleted(message: String) {
        val event = EventEnvelopeJson.deserialize(message, RoomDeletedPayload::class.java)
        val deleted = files.deleteByRoom(UUID.fromString(event.payload.roomId))
        log.info { "room.deleted ${event.payload.roomId}: removed $deleted files" }
    }

    @KafkaListener(topics = [EventEnvelope.Companion.Topics.USER_DELETED], groupId = "\${spring.kafka.consumer.group-id:file-service}")
    fun onUserDeleted(message: String) {
        val event = EventEnvelopeJson.deserialize(message, UserDeletedPayload::class.java)
        val deleted = files.deleteAvatarsOwnedBy(UUID.fromString(event.payload.userId))
        log.info { "user.deleted ${event.payload.userId}: removed $deleted avatars" }
    }
}
