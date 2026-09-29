package com.drift.message.controller

import com.drift.message.dto.MessagePageDto
import com.drift.message.exceptions.DomainError
import com.drift.message.interfaces.IMessageRenderer
import com.drift.message.interfaces.IMessageService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** `GET /messages` (nginx exposes it as `/api/v1/messages`), spec section 14: paginated history, newest first. */
@RestController
class MessageController(
    private val messages: IMessageService,
    private val renderer: IMessageRenderer,
) {
    @GetMapping("/messages")
    fun history(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam roomId: UUID,
        @RequestParam(required = false) before: UUID?,
        @RequestParam(required = false) limit: Int?,
    ): MessagePageDto {
        val page = messages.history(userId(jwt), roomId, before, limit)
        return MessagePageDto(renderer.render(page.messages), page.nextCursor)
    }
}

/** The signed-in user's id from the session JWT. */
internal fun userId(jwt: Jwt): UUID =
    runCatching { UUID.fromString(jwt.subject) }.getOrElse { throw DomainError.Unauthorized("Invalid session.") }
