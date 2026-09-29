package com.drift.message.controller

import com.drift.message.dto.MessageDto
import com.drift.message.dto.StartUploadRequest
import com.drift.message.dto.UploadStartedDto
import com.drift.message.dto.UploadStatusDto
import com.drift.message.exceptions.DomainError
import com.drift.message.interfaces.IMessageRenderer
import com.drift.message.interfaces.IUploadService
import jakarta.servlet.http.HttpServletRequest
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
import org.springframework.web.bind.annotation.RestController

/**
 * Chunked, resumable uploads (spec sections 13.6 and 14; nginx exposes these under `/api/v1`). Thin: the chunk body is
 * handed to the service as a stream, never read into memory here.
 */
@RestController
class UploadController(
    private val uploads: IUploadService,
    private val renderer: IMessageRenderer,
) {
    @PostMapping("/messages/uploads")
    fun start(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: StartUploadRequest,
    ): ResponseEntity<UploadStartedDto> {
        val session = uploads.start(userId(jwt), request.roomId, request.fileName, request.size, request.mimeType)
        return ResponseEntity.status(HttpStatus.CREATED).body(UploadStartedDto(session.id, session.chunkSize, session.totalChunks))
    }

    @PutMapping("/messages/uploads/{id}/chunks/{index}")
    fun chunk(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: String,
        @PathVariable index: Int,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        val length = request.contentLengthLong
        if (length < 0) throw DomainError.ValidationFailed("A Content-Length header is required for chunk uploads.")
        uploads.uploadChunk(userId(jwt), id, index, length, request.inputStream)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/messages/uploads/{id}")
    fun status(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: String,
    ): UploadStatusDto {
        val (session, received) = uploads.status(userId(jwt), id)
        return UploadStatusDto(
            uploadId = session.id,
            roomId = session.roomId,
            fileName = session.fileName,
            size = session.sizeBytes,
            chunkSize = session.chunkSize,
            totalChunks = session.totalChunks,
            received = received.sorted(),
            missing = (0 until session.totalChunks).filter { it !in received },
        )
    }

    @PostMapping("/messages/uploads/{id}/complete")
    fun complete(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: String,
    ): MessageDto = renderer.render(listOf(uploads.complete(userId(jwt), id))).first()

    @DeleteMapping("/messages/uploads/{id}")
    fun cancel(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: String,
    ): ResponseEntity<Void> {
        uploads.cancel(userId(jwt), id)
        return ResponseEntity.noContent().build()
    }
}
