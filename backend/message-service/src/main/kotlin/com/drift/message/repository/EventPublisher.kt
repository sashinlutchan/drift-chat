package com.drift.message.repository

import com.drift.message.common.EventEnvelope
import com.drift.message.common.EventEnvelopeJson
import com.drift.message.interfaces.IEventPublisher
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
        key: String,
        envelope: EventEnvelope<T>,
    ) {
        val stamped = envelope.copy(timestamp = clock.instant())
        kafka.send(topic, key, EventEnvelopeJson.serialize(stamped))
    }
}
