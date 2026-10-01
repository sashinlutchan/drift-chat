package com.drift.room.controller

import com.drift.room.config.AppProperties
import com.drift.room.dto.CreateRoomRequest
import com.drift.room.dto.MemberDto
import com.drift.room.dto.RoomDto
import com.drift.room.dto.RoomInfoDto
import com.drift.room.exceptions.DomainError
import com.drift.room.interfaces.IRoomService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class RoomController(
    private val rooms: IRoomService,
    private val properties: AppProperties,
) {
    @GetMapping("/rooms")
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) search: String?,
    ): List<RoomDto> = rooms.list(userId(jwt), search).map(::dto)

    @PostMapping("/rooms")
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: CreateRoomRequest,
    ): ResponseEntity<RoomDto> = ResponseEntity.status(HttpStatus.CREATED).body(dto(rooms.create(userId(jwt), request.name)))

    @GetMapping("/rooms/{id}")
    fun info(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
    ): RoomInfoDto = RoomInfoDto.from(rooms.info(id, userId(jwt)), properties.publicBaseUrl)

    @GetMapping("/rooms/by-slug/{slug}")
    fun bySlug(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable slug: String,
    ): RoomDto = dto(rooms.bySlug(slug, userId(jwt)))

    @GetMapping("/rooms/{id}/members")
    fun members(
        @PathVariable id: UUID,
    ): List<MemberDto> = rooms.members(id).map(MemberDto::from)

    @PostMapping("/rooms/{id}/join")
    fun join(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
    ): RoomDto = dto(rooms.join(id, userId(jwt)))

    @PostMapping("/rooms/{id}/leave")
    fun leave(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        rooms.leave(id, userId(jwt))
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/rooms/{id}/pin")
    fun pin(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        rooms.pin(id, userId(jwt))
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/rooms/{id}/pin")
    fun unpin(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        rooms.unpin(id, userId(jwt))
        return ResponseEntity.noContent().build()
    }

    private fun dto(summary: com.drift.room.models.RoomSummary) = RoomDto.from(summary, properties.publicBaseUrl)

    private fun userId(jwt: Jwt): UUID =
        runCatching {
            UUID.fromString(jwt.subject)
        }.getOrElse { throw DomainError.Unauthorized("Invalid session.") }
}
