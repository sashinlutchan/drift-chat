package com.drift.user.utils

/**
 * Username rules (spec section 3.1): 3–20 characters, letters, numbers, `_` and `-` only.
 */
object UsernameValidator {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 20
    private val PATTERN = Regex("^[A-Za-z0-9_-]{$MIN_LENGTH,$MAX_LENGTH}$")

    fun isValid(username: String): Boolean = PATTERN.matches(username)
}
