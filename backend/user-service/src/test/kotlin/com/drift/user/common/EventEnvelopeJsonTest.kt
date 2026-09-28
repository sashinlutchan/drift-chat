package com.drift.user.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class EventEnvelopeJsonTest {
    data class Payload(
        val userId: String,
    )

    @Test
    fun `serialize and deserialize round trip keeps all fields`() {
        val envelope =
            EventEnvelope(
                type = "user.deleted",
                timestamp = Instant.parse("2026-01-01T00:00:00Z"),
                traceId = "trace-123",
                payload = Payload("user-1"),
            )
        val json = EventEnvelopeJson.serialize(envelope)
        val back = EventEnvelopeJson.deserialize(json, Payload::class.java)

        assertThat(back.type).isEqualTo("user.deleted")
        assertThat(back.version).isEqualTo(1)
        assertThat(back.timestamp).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"))
        assertThat(back.traceId).isEqualTo("trace-123")
        assertThat(back.payload).isEqualTo(Payload("user-1"))
        assertThat(back.id).isNotBlank()
    }

    @Test
    fun `json carries event id type version timestamp and trace context`() {
        val json = EventEnvelopeJson.serialize(EventEnvelope(type = "t", timestamp = Instant.EPOCH, payload = 1))
        assertThat(json)
            .contains("\"id\"")
            .contains("\"type\"")
            .contains("\"version\"")
            .contains("\"timestamp\"")
            .contains("\"traceId\"")
    }
}
