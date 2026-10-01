package com.drift.message.service

import com.drift.message.dto.TypingUpdateDto
import com.drift.message.interfaces.IConnectionHub
import com.drift.message.interfaces.IMessageRenderer
import com.drift.message.interfaces.ITypingStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class TypingBroadcaster(
    private val typing: ITypingStore,
    private val renderer: IMessageRenderer,
    private val hub: IConnectionHub,
    private val clock: Clock,
) {
    fun broadcast(roomId: UUID) {
        try {
            val who = renderer.senders(typing.active(roomId, clock.instant()))
            hub.sendToRoom(roomId, "typing:update", TypingUpdateDto(roomId, who))
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.warn { "Could not broadcast typing for $roomId: ${e.message}" }
        }
    }
}
