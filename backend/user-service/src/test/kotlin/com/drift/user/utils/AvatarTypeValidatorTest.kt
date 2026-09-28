package com.drift.user.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AvatarTypeValidatorTest {
    @ParameterizedTest
    @ValueSource(strings = ["image/jpeg", "image/png", "image/webp", "IMAGE/PNG"])
    fun `accepts allowed image types`(contentType: String) {
        assertThat(AvatarTypeValidator.isAllowed(contentType)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["image/gif", "application/pdf", "video/mp4", ""])
    fun `rejects disallowed types`(contentType: String) {
        assertThat(AvatarTypeValidator.isAllowed(contentType)).isFalse()
    }
}
