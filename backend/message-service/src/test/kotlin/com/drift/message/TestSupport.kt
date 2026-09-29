package com.drift.message

import com.drift.message.common.MessageCipher
import com.drift.message.config.AppProperties
import org.springframework.util.unit.DataSize
import java.time.Duration

/** Builders shared by the unit tests. */
object TestSupport {
    val cipher = MessageCipher(mapOf(1 to ByteArray(32) { it.toByte() }), 1)

    fun properties(
        maxMessageLength: Int = 2000,
        maxFile: DataSize = DataSize.ofGigabytes(1),
        chunk: DataSize = DataSize.ofMegabytes(8),
        pageSize: Int = 50,
        allowedOrigins: List<String> = listOf("https://drift.test"),
    ) = AppProperties(
        jwtSecret = "x".repeat(32),
        sessionCookieName = "drift_session",
        messageEncryptionKey = "unused",
        messageEncryptionKeyVersion = 1,
        messageEncryptionPreviousKeys = "",
        maxMessageLength = maxMessageLength,
        maxFileSize = maxFile,
        uploadChunkSize = chunk,
        uploadSessionTtl = Duration.ofHours(1),
        historyPageSize = pageSize,
        allowedMimeTypes = listOf("image/png", "video/mp4", "audio/mpeg"),
        socketStaleAfter = Duration.ofSeconds(90),
        userServiceAddr = "u:1",
        roomServiceAddr = "r:1",
        fileServiceAddr = "f:1",
        socket =
            AppProperties.Socket(
                port = 0,
                enabled = false,
                allowedOrigins = allowedOrigins,
                pingInterval = Duration.ofSeconds(25),
                pingTimeout = Duration.ofSeconds(60),
                shutdownGrace = Duration.ofSeconds(30),
            ),
    )
}
