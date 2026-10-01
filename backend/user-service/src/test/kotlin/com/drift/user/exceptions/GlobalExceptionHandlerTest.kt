package com.drift.user.exceptions

import jakarta.validation.ConstraintViolationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.multipart.MaxUploadSizeExceededException

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun `domain errors keep their status and code`() {
        val response = handler.handleDomainError(DomainError.UsernameTaken("This username is taken."))

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body?.properties?.get("code")).isEqualTo("USERNAME_TAKEN")
    }

    @Test
    fun `unexpected errors become a generic 500 that does not leak the message`() {
        val response = handler.handleUnexpected(IllegalStateException("db password is hunter2"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body?.detail).doesNotContain("hunter2")
        assertThat(response.body?.properties?.get("code")).isEqualTo("INTERNAL_ERROR")
    }

    @Test
    fun `oversized uploads map to 413`() {
        val response =
            handler.handleMaxUploadSizeExceededException(
                MaxUploadSizeExceededException(1),
                HttpHeaders(),
                HttpStatus.PAYLOAD_TOO_LARGE,
                ServletWebRequest(MockHttpServletRequest()),
            ) as ResponseEntity<ProblemDetail>

        assertThat(response.statusCode).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
        assertThat(response.body?.properties?.get("code")).isEqualTo("PAYLOAD_TOO_LARGE")
    }

    @Test
    fun `constraint violations map to validation failed`() {
        val response = handler.handleConstraintViolation(ConstraintViolationException("bad", emptySet()))

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.properties?.get("code")).isEqualTo("VALIDATION_FAILED")
    }
}
