package com.drift.message.dto

import java.util.UUID

data class TypingUpdateDto(
    val roomId: UUID,
    val users: List<SenderDto>,
)

data class SocketErrorDto(
    val event: String,
    val code: String,
    val message: String,
)
