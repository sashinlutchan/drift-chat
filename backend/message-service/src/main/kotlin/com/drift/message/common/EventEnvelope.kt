package com.drift.message.common

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

/**
 * Versioned event envelope for every Kafka message (spec section 17):
 * event id, type, version, timestamp and trace context.
 */
data class EventEnvelope<T>(
    @JsonProperty("id") val id: String = UUID.randomUUID().toString(),
    @JsonProperty("type") val type: String,
    @JsonProperty("version") val version: Int = 1,
    @JsonProperty("timestamp") val timestamp: Instant,
    @JsonProperty("traceId") val traceId: String? = null,
    @JsonProperty("payload") val payload: T,
) {
    companion object {
        const val SCHEMA_VERSION = 1

        /** Kafka topic names (spec section 17). */
        object Topics {
            const val USER_DELETED = "user.deleted"
            const val USER_UPDATED = "user.updated"
            const val PRESENCE_CHANGED = "presence.changed"
            const val ROOM_CREATED = "room.created"
            const val ROOM_UPDATED = "room.updated"
            const val ROOM_DELETED = "room.deleted"
            const val MEMBER_JOINED = "member.joined"
            const val MEMBER_LEFT = "member.left"
            const val ROOM_OCCUPANCY = "room.occupancy"
            const val MESSAGE_CREATED = "message.created"
        }
    }
}
