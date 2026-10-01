package com.drift.room.interfaces

import com.drift.room.common.EventEnvelope

interface IEventPublisher {
    fun <T> publish(
        topic: String,
        envelope: EventEnvelope<T>,
    )
}
