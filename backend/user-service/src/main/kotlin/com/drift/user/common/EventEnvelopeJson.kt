package com.drift.user.common

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.time.Instant

object EventEnvelopeJson {
    private val mapper =
        ObjectMapper()
            .registerKotlinModule()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .registerModule(
                com.fasterxml.jackson.datatype.jsr310
                    .JavaTimeModule(),
            )

    fun <T> serialize(envelope: EventEnvelope<T>): String = mapper.writeValueAsString(envelope)

    fun <T> deserialize(
        json: String,
        payloadClass: Class<T>,
    ): EventEnvelope<T> {
        val raw = mapper.readValue(json, RawEnvelope::class.java)
        val payload = mapper.convertValue(raw.payload, payloadClass)
        return EventEnvelope(
            id = raw.id ?: "",
            type = raw.type ?: "",
            version = raw.version ?: 1,
            timestamp = raw.timestamp ?: Instant.EPOCH,
            traceId = raw.traceId,
            payload = payload,
        )
    }

    data class RawEnvelope(
        val id: String? = null,
        val type: String? = null,
        val version: Int? = null,
        val timestamp: Instant? = null,
        val traceId: String? = null,
        val payload: Any? = null,
    )
}
