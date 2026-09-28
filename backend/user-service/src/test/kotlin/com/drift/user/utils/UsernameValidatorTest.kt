package com.drift.user.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class UsernameValidatorTest {
    @ParameterizedTest
    @ValueSource(strings = ["sipho", "thandi_23", "User-42", "abc", "a_b-c12"])
    fun `accepts valid usernames`(username: String) {
        assertThat(UsernameValidator.isValid(username)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["ab", "this_username_is_way_too_long", "has space", "bad!char", "", "br<ackets"])
    fun `rejects invalid usernames`(username: String) {
        assertThat(UsernameValidator.isValid(username)).isFalse()
    }
}
