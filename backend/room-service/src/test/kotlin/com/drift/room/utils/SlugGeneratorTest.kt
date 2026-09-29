package com.drift.room.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SlugGeneratorTest {
    @ParameterizedTest
    @CsvSource(
        "Movie Night,movie-night",
        "  Café   Crème!!  ,cafe-creme",
        "UPPER_case-mix 42,upper-case-mix-42",
        "😀😀,''",
    )
    fun `slugify makes a url safe base`(
        name: String,
        expected: String,
    ) {
        assertThat(SlugGenerator.slugify(name)).isEqualTo(expected)
    }

    @Test
    fun `generated slugs are url safe, suffixed and different every time`() {
        val slugs = (1..50).map { SlugGenerator.generate("Movie Night") }.toSet()

        assertThat(slugs).hasSize(50)
        assertThat(slugs).allMatch { it.matches(Regex("movie-night-[a-z0-9]{6}")) }
    }

    @Test
    fun `a name with nothing usable still gets a slug`() {
        assertThat(SlugGenerator.generate("😀")).matches("room-[a-z0-9]{6}")
    }

    @Test
    fun `long names are cut so links stay short`() {
        assertThat(SlugGenerator.generate("a".repeat(200)).length).isLessThanOrEqualTo(47)
    }
}
