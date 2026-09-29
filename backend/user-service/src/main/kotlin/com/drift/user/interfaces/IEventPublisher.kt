package com.drift.user.interfaces

import com.drift.user.common.EventEnvelope

/**
 * Event publisher port (Kafka-backed, spec section 17). Implemented in `events/`.
 * The service layer publishes domain events without knowing about Kafka.
 */
interface IEventPublisher {
    fun <T> publish(
        topic: String,
        envelope: EventEnvelope<T>,
    )
}
