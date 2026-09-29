package com.drift.file.controller

import com.drift.file.exceptions.DomainError
import com.drift.file.interfaces.IFileService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.util.UUID

/**
 * `GET /files/{id}` (nginx exposes it as `/api/v1/files/{id}`), spec section 13.4: streams a file with HTTP Range
 * support so audio and video can seek. Plain `<img>`, `<audio>` and `<video>` tags work because the session cookie
 * is sent automatically. Thin — access rules live in the service.
 */
@RestController
class FileController(
    private val files: IFileService,
) {
    @GetMapping("/files/{id}")
    fun download(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader(HttpHeaders.RANGE, required = false) range: String?,
    ): ResponseEntity<StreamingResponseBody> {
        val userId = runCatching { UUID.fromString(jwt.subject) }.getOrElse { throw DomainError.Unauthorized("Invalid session.") }
        val content = files.open(id, userId, range)
        val body = StreamingResponseBody { out -> content.open().use { it.transferTo(out) } }
        val builder =
            ResponseEntity
                .status(if (content.partial) HttpStatus.PARTIAL_CONTENT else HttpStatus.OK)
                .contentType(MediaType.parseMediaType(content.record.mimeType))
                .contentLength(content.range.length)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
        if (content.partial) {
            builder.header(HttpHeaders.CONTENT_RANGE, "bytes ${content.range.start}-${content.range.end}/${content.record.sizeBytes}")
        }
        return builder.body(body)
    }
}
