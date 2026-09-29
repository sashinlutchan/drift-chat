package com.drift.file.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Base64

class MimeSnifferTest {
    private val sniffer = MimeSniffer()

    private val png =
        Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
        )

    @Test
    fun `detects a png from its bytes whatever the client says`() {
        assertThat(sniffer.detect(png)).isEqualTo("image/png")
    }

    @Test
    fun `detects jpeg magic bytes`() {
        val jpeg =
            byteArrayOf(
                0xFF.toByte(),
                0xD8.toByte(),
                0xFF.toByte(),
                0xE0.toByte(),
                0,
                0x10,
                'J'.code.toByte(),
                'F'.code.toByte(),
                'I'.code.toByte(),
                'F'.code.toByte(),
                0,
            )
        assertThat(sniffer.detect(jpeg)).isEqualTo("image/jpeg")
    }

    @Test
    fun `an executable pretending to be an image is not an image`() {
        val script = "#!/bin/sh\nrm -rf /\n".toByteArray()
        assertThat(sniffer.detect(script)).doesNotStartWith("image/")
    }

    @Test
    fun `aliases are folded onto allow-list names`() {
        assertThat(MimeSniffer.normalize("audio/x-wav")).isEqualTo("audio/wav")
        assertThat(MimeSniffer.normalize("Audio/Vnd.Wave")).isEqualTo("audio/wav")
        assertThat(MimeSniffer.normalize("application/ogg")).isEqualTo("audio/ogg")
        assertThat(MimeSniffer.normalize("video/MP4")).isEqualTo("video/mp4")
    }
}
