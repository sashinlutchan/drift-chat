package com.drift.file.utils

import com.drift.file.exceptions.DomainError
import com.drift.file.models.ByteRange
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class RangeParserTest {
    @ParameterizedTest
    @CsvSource(
        "bytes=0-99,1000,0,99",
        "bytes=500-,1000,500,999",
        "bytes=-100,1000,900,999",
        "bytes=900-5000,1000,900,999",
        "bytes=-5000,1000,0,999",
        "bytes=0-0,1,0,0",
    )
    fun `parses a single range and clamps it to the file`(
        header: String,
        size: Long,
        start: Long,
        end: Long,
    ) {
        assertThat(RangeParser.parse(header, size)).isEqualTo(ByteRange(start, end))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "bytes=", "bytes=-", "items=0-5", "bytes=0-5,10-20", "garbage"])
    fun `absent or unusable headers mean the whole file`(header: String) {
        assertThat(RangeParser.parse(header, 1000)).isNull()
    }

    @Test
    fun `null header means the whole file`() {
        assertThat(RangeParser.parse(null, 1000)).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["bytes=1000-", "bytes=2000-3000", "bytes=-0", "bytes=50-10"])
    fun `ranges outside the file are not satisfiable`(header: String) {
        assertThatThrownBy { RangeParser.parse(header, 1000) }.isInstanceOf(DomainError.RangeNotSatisfiable::class.java)
    }

    @Test
    fun `any range on an empty file is not satisfiable`() {
        assertThatThrownBy { RangeParser.parse("bytes=0-", 0) }.isInstanceOf(DomainError.RangeNotSatisfiable::class.java)
    }

    @Test
    fun `range length is inclusive`() {
        assertThat(ByteRange(10, 19).length).isEqualTo(10)
    }
}
