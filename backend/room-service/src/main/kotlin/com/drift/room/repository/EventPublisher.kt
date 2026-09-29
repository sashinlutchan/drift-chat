package com.drift.room.repository

import com.drift.room.common.EventEnvelope
import com.drift.room.common.EventEnvelopeJson
import com.drift.room.interfaces.IEventPublisher
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.Clock

/** Kafka-backed [IEventPublisher]. */
@Component
class EventPublisher(
    private val kafka: KafkaTemplate<String, String>,
    private val clock: Clock,
) : IEventPublisher {
    override fun <T> publish(
        topic: String,
        envelope: EventEnvelope<T>,
    ) {
        val stamped = envelope.copy(timestamp = clock.instant())
        kafka.send(topic, stamped.id, EventEnvelopeJson.serialize(stamped))
    }
}
