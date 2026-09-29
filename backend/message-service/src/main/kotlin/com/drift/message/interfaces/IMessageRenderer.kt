package com.drift.message.interfaces

import com.drift.message.dto.MessageDto
import com.drift.message.dto.SenderDto
import com.drift.message.models.Message
import java.util.UUID

/** Turns decrypted messages into client-facing DTOs with sender details. Implemented in `service/MessageRenderer`. */
interface IMessageRenderer {
    fun render(messages: List<Message>): List<MessageDto>

    /** Sender details for people (used by the typing indicator); deleted users are left out. */
    fun senders(ids: Collection<UUID>): List<SenderDto>

    /** Forgets a cached profile because the user changed or was deleted. */
    fun invalidate(userId: UUID)
}
