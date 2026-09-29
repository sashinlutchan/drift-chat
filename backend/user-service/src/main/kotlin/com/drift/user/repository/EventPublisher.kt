package com.drift.user.repository

import com.drift.user.common.EventEnvelope
import com.drift.user.common.EventEnvelopeJson
import com.drift.user.interfaces.IEventPublisher
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/** Kafka-backed event publisher implementing the domain port [IEventPublisher]. */
@Component
class EventPublisher(
    private val kafka: KafkaTemplate<String, String>,
    private val clock: Clock,
) : IEventPublisher {
    override fun <T> publish(
        topic: String,
        envelope: EventEnvelope<T>,
    ) {
        val stamped = envelope.copy(timestamp = Instant.now(clock))
        kafka.send(topic, stamped.id, EventEnvelopeJson.serialize(stamped))
    }
}
