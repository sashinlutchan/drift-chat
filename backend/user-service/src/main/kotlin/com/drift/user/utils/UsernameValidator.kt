package com.drift.user.utils

object UsernameValidator {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 20
    private val PATTERN = Regex("^[A-Za-z0-9_-]{$MIN_LENGTH,$MAX_LENGTH}$")

    fun isValid(username: String): Boolean = PATTERN.matches(username)
}
