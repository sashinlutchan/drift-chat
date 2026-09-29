package com.drift.file.utils

import com.drift.file.interfaces.IMimeSniffer
import org.apache.tika.Tika
import org.springframework.stereotype.Component

/** Tika-backed [IMimeSniffer]: the type comes from the file's magic bytes, never from what the client says. */
@Component
class MimeSniffer : IMimeSniffer {
    private val tika = Tika()

    override fun detect(head: ByteArray): String = normalize(tika.detect(head))

    companion object {
        /** Folds common aliases onto the names the allow-list uses. */
        fun normalize(mime: String): String = ALIASES[mime.lowercase()] ?: mime.lowercase()

        private val ALIASES =
            mapOf(
                "audio/x-wav" to "audio/wav",
                "audio/vnd.wave" to "audio/wav",
                "audio/x-mpeg" to "audio/mpeg",
                "audio/x-m4a" to "audio/mp4",
                "application/ogg" to "audio/ogg",
                "application/mp4" to "video/mp4",
            )
    }
}
