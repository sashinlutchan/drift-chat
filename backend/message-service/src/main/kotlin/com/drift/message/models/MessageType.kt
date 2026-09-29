package com.drift.message.models

/** What a message contains (spec section 7.3). Stored lower-case in `messages.type`. */
enum class MessageType {
    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    SYSTEM,
    ;

    val dbValue: String get() = name.lowercase()

    companion object {
        fun fromDb(value: String): MessageType = valueOf(value.uppercase())

        /** The message type for a file, from its sniffed MIME type (`video/mp4` → VIDEO). */
        fun forMime(mime: String): MessageType? =
            when (mime.substringBefore('/').lowercase()) {
                "image" -> IMAGE
                "audio" -> AUDIO
                "video" -> VIDEO
                else -> null
            }
    }
}
