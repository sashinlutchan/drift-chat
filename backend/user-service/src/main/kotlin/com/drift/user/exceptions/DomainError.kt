package com.drift.user.exceptions

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

sealed class DomainError(
    val status: HttpStatus,
    val code: ErrorCode,
    val detail: String,
) : RuntimeException(detail) {
    class ValidationFailed(
        detail: String,
    ) : DomainError(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, detail)

    class UsernameTaken(
        detail: String,
    ) : DomainError(HttpStatus.CONFLICT, ErrorCode.USERNAME_TAKEN, detail)

    class UsernameInvalid(
        detail: String,
    ) : DomainError(HttpStatus.BAD_REQUEST, ErrorCode.USERNAME_INVALID, detail)

    class NotFound(
        detail: String,
    ) : DomainError(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, detail)

    class Unauthorized(
        detail: String,
    ) : DomainError(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, detail)

    class Forbidden(
        detail: String,
    ) : DomainError(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, detail)

    class PayloadTooLarge(
        detail: String,
    ) : DomainError(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE, detail)

    class UnsupportedMediaType(
        detail: String,
    ) : DomainError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE, detail)
}

fun DomainError.toProblemDetail(): ProblemDetail =
    ProblemDetail.forStatusAndDetail(status, detail).apply {
        title = status.toString()
        setProperty("code", code.name)
    }
