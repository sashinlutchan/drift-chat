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

    private fun resource(name: String) = javaClass.getResourceAsStream("/media/$name")!!.readAllBytes()

    @Test
    fun `a webm recorded by a browser is video webm, not generic matroska`() {
        assertThat(sniffer.detect(resource("recorded-head.webm"))).isEqualTo("video/webm")
    }

    @Test
    fun `a real matroska file is still not webm`() {
        val mkv =
            resource("recorded-head.webm").also { bytes ->
                val at = (0..60).first { i -> bytes[i] == 0x42.toByte() && bytes[i + 1] == 0x82.toByte() }
                "webm".toByteArray().copyInto(bytes, at + 3)
                "matr".toByteArray().copyInto(bytes, at + 3)
            }

        assertThat(sniffer.detect(mkv)).isNotEqualTo("video/webm")
    }

    @Test
    fun `a wav file is detected as wav`() {
        assertThat(sniffer.detect(resource("tone-head.wav"))).isEqualTo("audio/wav")
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

    @Test
    fun `a gif is detected from its bytes`() {
        assertThat(sniffer.detect("GIF89a\u0001\u0000\u0001\u0000".toByteArray(Charsets.ISO_8859_1))).isEqualTo("image/gif")
    }

    @Test
    fun `svg and html are detected as what they are and flagged as able to run code`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>""".toByteArray()
        val html = "<!DOCTYPE html><html><body><script>alert(1)</script></body></html>".toByteArray()

        assertThat(MimeSniffer.canExecute(sniffer.detect(svg))).isTrue()
        assertThat(MimeSniffer.canExecute(sniffer.detect(html))).isTrue()
    }

    @Test
    fun `an svg stays refused even when someone puts it on the allow-list`() {
        listOf("image/svg+xml", "text/html", "application/xhtml+xml", "application/javascript", "text/xml").forEach {
            assertThat(MimeSniffer.canExecute(it)).describedAs(it).isTrue()
        }
    }

    @Test
    fun `ordinary pictures, audio and video are not flagged`() {
        listOf(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/avif",
            "image/bmp",
            "audio/mpeg",
            "audio/ogg",
            "audio/wav",
            "audio/webm",
            "audio/mp4",
            "audio/aac",
            "audio/flac",
            "video/mp4",
            "video/webm",
            "video/ogg",
            "video/quicktime",
        ).forEach { assertThat(MimeSniffer.canExecute(it)).describedAs(it).isFalse() }
    }

    @Test
    fun `more aliases are folded onto allow-list names`() {
        assertThat(MimeSniffer.normalize("audio/mp3")).isEqualTo("audio/mpeg")
        assertThat(MimeSniffer.normalize("audio/vorbis")).isEqualTo("audio/ogg")
        assertThat(MimeSniffer.normalize("audio/x-flac")).isEqualTo("audio/flac")
        assertThat(MimeSniffer.normalize("audio/x-aac")).isEqualTo("audio/aac")
        assertThat(MimeSniffer.normalize("image/x-ms-bmp")).isEqualTo("image/bmp")
        assertThat(MimeSniffer.normalize("video/x-m4v")).isEqualTo("video/mp4")
    }
}
