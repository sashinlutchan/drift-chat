package com.drift.user.domain

import com.drift.user.common.EventEnvelope

/**
 * Event publisher port (Kafka-backed, spec section 17). Implemented in `events/`.
 * The service layer publishes domain events without knowing about Kafka.
 */
interface EventPublisher {
    fun <T> publish(
        topic: String,
        envelope: EventEnvelope<T>,
    )
}
