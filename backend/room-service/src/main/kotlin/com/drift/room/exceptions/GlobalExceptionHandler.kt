package com.drift.room.exceptions

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

private val log = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(DomainError::class)
    fun handleDomainError(error: DomainError): ResponseEntity<ProblemDetail> {
        log.debug { "Domain error ${error.code}: ${error.detail}" }
        return ResponseEntity.status(error.status).body(error.toProblemDetail())
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(error: ConstraintViolationException): ResponseEntity<ProblemDetail> =
        handleDomainError(DomainError.ValidationFailed(error.constraintViolations.joinToString("; ") { it.message }))

    public override fun handleMaxUploadSizeExceededException(
        ex: MaxUploadSizeExceededException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        log.debug { "Upload too large: ${ex.message}" }
        val problem = DomainError.PayloadTooLarge("The uploaded file is too large.").toProblemDetail()
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problem)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(error: Exception): ResponseEntity<ProblemDetail> {
        log.error(error) { "Unhandled exception" }
        val problem =
            ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.").apply {
                title = HttpStatus.INTERNAL_SERVER_ERROR.toString()
                setProperty("code", ErrorCode.INTERNAL_ERROR.name)
            }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem)
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val response = super.handleExceptionInternal(ex, body, headers, statusCode, request)
        val problem = response?.body as? ProblemDetail
        if (problem != null && problem.properties?.containsKey("code") != true) {
            problem.setProperty("code", codeFor(ex, statusCode).name)
        }
        return response
    }

    private fun codeFor(
        ex: Exception,
        status: HttpStatusCode,
    ): ErrorCode =
        when {
            ex is MethodArgumentNotValidException -> ErrorCode.VALIDATION_FAILED
            status.value() == HttpStatus.NOT_FOUND.value() -> ErrorCode.NOT_FOUND
            status.value() == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value() -> ErrorCode.UNSUPPORTED_MEDIA_TYPE
            status.is5xxServerError -> ErrorCode.INTERNAL_ERROR
            else -> ErrorCode.VALIDATION_FAILED
        }
}
