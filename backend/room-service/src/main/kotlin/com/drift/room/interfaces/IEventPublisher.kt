package com.drift.room.interfaces

import com.drift.room.common.EventEnvelope

/** Event publisher port (Kafka-backed, spec section 17). Implemented in `repository/`. */
interface IEventPublisher {
    fun <T> publish(
        topic: String,
        envelope: EventEnvelope<T>,
    )
}
