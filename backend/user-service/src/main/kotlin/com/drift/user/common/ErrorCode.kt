package com.drift.user.common

/**
 * Machine-readable error codes returned in Problem Details responses
 * (spec section 14: `application/problem+json` with an extra `code` field).
 */
enum class ErrorCode {
    VALIDATION_FAILED,
    USERNAME_TAKEN,
    USERNAME_INVALID,
    NOT_FOUND,
    UNAUTHORIZED,
    FORBIDDEN,
    PAYLOAD_TOO_LARGE,
    UNSUPPORTED_MEDIA_TYPE,
    INTERNAL,
}
