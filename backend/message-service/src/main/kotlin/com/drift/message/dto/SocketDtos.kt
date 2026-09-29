package com.drift.message.dto

import java.util.UUID

/** `typing:update`: who is typing in a room right now (spec section 7.5). */
data class TypingUpdateDto(
    val roomId: UUID,
    val users: List<SenderDto>,
)

/** `error`: a structured error for the socket that caused it (spec section 15). */
data class SocketErrorDto(
    val event: String,
    val code: String,
    val message: String,
)
