package com.drift.message.config

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.util.unit.DataSize
import java.time.Duration

@ConfigurationProperties("drift")
data class AppProperties(
    @field:NotBlank val jwtSecret: String,
    @DefaultValue("drift_session") val sessionCookieName: String,
    @field:NotBlank val messageEncryptionKey: String,
    @DefaultValue("1") val messageEncryptionKeyVersion: Int,
    @DefaultValue("") val messageEncryptionPreviousKeys: String,
    @field:Min(1) @DefaultValue("2000") val maxMessageLength: Int,
    @DefaultValue("5GB") val maxFileSize: DataSize,
    @DefaultValue("8MB") val uploadChunkSize: DataSize,
    @DefaultValue("1h") val uploadSessionTtl: Duration,
    @field:Min(1) @DefaultValue("50") val historyPageSize: Int,
    @DefaultValue(
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
    )
    val allowedMimeTypes: List<String>,
    @DefaultValue("90s") val socketStaleAfter: Duration,
    @NotBlank val userServiceAddr: String,
    @NotBlank val roomServiceAddr: String,
    @NotBlank val fileServiceAddr: String,
    val socket: Socket,
) {
    init {
        require(uploadChunkSize.toBytes() >= MIN_CHUNK_BYTES) { "UPLOAD_CHUNK_SIZE must be at least 5MB (MinIO's minimum part size)" }
    }

    data class Socket(
        @DefaultValue("8090") val port: Int,
        @DefaultValue("true") val enabled: Boolean,
        @DefaultValue("https://localhost:8443") val allowedOrigins: List<String>,
        @DefaultValue("25s") val pingInterval: Duration,
        @DefaultValue("60s") val pingTimeout: Duration,
        @DefaultValue("30s") val shutdownGrace: Duration,
    )

    companion object {
        const val MIN_CHUNK_BYTES = 5L * 1024 * 1024
    }
}
