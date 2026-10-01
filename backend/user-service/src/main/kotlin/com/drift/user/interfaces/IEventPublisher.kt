package com.drift.user.interfaces

import com.drift.user.common.EventEnvelope

interface IEventPublisher {
    fun <T> publish(
        topic: String,
        envelope: EventEnvelope<T>,
    )
}
