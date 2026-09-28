package com.drift.user.common

import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Maps domain errors to RFC 9457 Problem Details responses with an extra `code` field
 * (spec sections 14 & 21).
 */
@RestControllerAdvice
class DomainExceptionHandler {
    @ExceptionHandler(Exception::class)
    fun handle(error: Exception): ResponseEntity<ProblemDetail> {
        val domainError = error as? DomainError ?: DomainError.ValidationFailed(error.message ?: "Unexpected error")
        return ResponseEntity.status(domainError.status).body(domainError.toProblemDetail())
    }
}
