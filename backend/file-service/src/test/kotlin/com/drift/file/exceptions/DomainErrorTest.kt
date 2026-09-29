package com.drift.file.exceptions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class DomainErrorTest {
    @Test
    fun `problem detail carries status detail and code`() {
        val problem = DomainError.UsernameTaken("This username is taken.").toProblemDetail()
        assertThat(problem.status).isEqualTo(HttpStatus.CONFLICT.value())
        assertThat(problem.detail).isEqualTo("This username is taken.")
        assertThat(problem.properties?.get("code")).isEqualTo("USERNAME_TAKEN")
    }

    @Test
    fun `each error maps to its HTTP status and code`() {
        assertThat(DomainError.ValidationFailed("x").status).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(DomainError.ValidationFailed("x").code).isEqualTo(ErrorCode.VALIDATION_FAILED)
        assertThat(DomainError.NotFound("x").status).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(DomainError.Unauthorized("x").status).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(DomainError.Forbidden("x").status).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(DomainError.UsernameInvalid("x").code).isEqualTo(ErrorCode.USERNAME_INVALID)
        assertThat(DomainError.PayloadTooLarge("x").status).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
        assertThat(DomainError.UnsupportedMediaType("x").status).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    }
}
