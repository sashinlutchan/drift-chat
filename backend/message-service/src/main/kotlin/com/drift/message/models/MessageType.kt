package com.drift.message.models

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

        fun forMime(mime: String): MessageType? =
            when (mime.substringBefore('/').lowercase()) {
                "image" -> IMAGE
                "audio" -> AUDIO
                "video" -> VIDEO
                else -> null
            }
    }
}
