package com.drift.message.interfaces

import com.drift.message.common.EventEnvelope

/** Event publisher port (Kafka-backed, spec section 17). Implemented in `repository/`. */
interface IEventPublisher {
    /** Publishes with [key] as the partition key, so events for one room stay in order. */
    fun <T> publish(
        topic: String,
        key: String,
        envelope: EventEnvelope<T>,
    )
}
