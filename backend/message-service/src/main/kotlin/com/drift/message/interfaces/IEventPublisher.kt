package com.drift.message.interfaces

import com.drift.message.common.EventEnvelope

interface IEventPublisher {
    fun <T> publish(
        topic: String,
        key: String,
        envelope: EventEnvelope<T>,
    )
}
