package com.drift.file.utils

import com.drift.file.interfaces.IMimeSniffer
import org.apache.tika.Tika
import org.springframework.stereotype.Component

@Component
class MimeSniffer : IMimeSniffer {
    private val tika = Tika()

    override fun detect(head: ByteArray): String {
        val detected = normalize(tika.detect(head))
        return if (detected in MATROSKA && hasWebmDocType(head)) "video/webm" else detected
    }

    private fun hasWebmDocType(head: ByteArray): Boolean {
        val marker =
            byteArrayOf(0x42, 0x82.toByte(), 0x84.toByte(), 'w'.code.toByte(), 'e'.code.toByte(), 'b'.code.toByte(), 'm'.code.toByte())
        val limit = minOf(head.size, DOCTYPE_SEARCH_BYTES) - marker.size
        return (0..limit).any { start -> marker.indices.all { head[start + it] == marker[it] } }
    }

    companion object {
        private const val DOCTYPE_SEARCH_BYTES = 64
        private val MATROSKA = setOf("video/x-matroska", "application/x-matroska", "audio/x-matroska")

        fun canExecute(mime: String): Boolean = EXECUTABLE_MARKERS.any { mime.lowercase().contains(it) }

        private val EXECUTABLE_MARKERS = listOf("svg", "xml", "html", "javascript", "ecmascript", "script", "executable", "x-sh")

        fun normalize(mime: String): String = ALIASES[mime.lowercase()] ?: mime.lowercase()

        private val ALIASES =
            mapOf(
                "audio/x-wav" to "audio/wav",
                "audio/vnd.wave" to "audio/wav",
                "audio/x-mpeg" to "audio/mpeg",
                "audio/x-m4a" to "audio/mp4",
                "application/ogg" to "audio/ogg",
                "application/mp4" to "video/mp4",
                "video/x-m4v" to "video/mp4",
                "audio/mp3" to "audio/mpeg",
                "audio/vorbis" to "audio/ogg",
                "audio/opus" to "audio/ogg",
                "audio/x-aac" to "audio/aac",
                "audio/x-flac" to "audio/flac",
                "image/x-ms-bmp" to "image/bmp",
            )
    }
}
