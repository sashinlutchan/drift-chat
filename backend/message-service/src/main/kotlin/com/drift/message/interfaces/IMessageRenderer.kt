package com.drift.message.interfaces

import com.drift.message.dto.MessageDto
import com.drift.message.dto.SenderDto
import com.drift.message.models.Message
import java.util.UUID

interface IMessageRenderer {
    fun render(messages: List<Message>): List<MessageDto>

    fun senders(ids: Collection<UUID>): List<SenderDto>

    fun invalidate(userId: UUID)
}
