package com.drift.file.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.util.unit.DataSize

@ConfigurationProperties("drift")
data class AppProperties(
    @field:NotBlank val jwtSecret: String,
    @DefaultValue("drift_session") val sessionCookieName: String,
    @DefaultValue("5MB") val maxAvatarSize: DataSize,
    @NotBlank val roomServiceAddr: String,
    val storage: Storage,
) {
    data class Storage(
        @field:NotBlank val minioEndpoint: String,
        @field:NotBlank val minioAccessKey: String,
        @field:NotBlank val minioSecretKey: String,
        @DefaultValue("drift-avatars") val minioBucketAvatars: String,
        @DefaultValue("drift-media") val minioBucketMedia: String,
        @DefaultValue("5GB") val maxFileSize: DataSize,
        @DefaultValue("2d") val abandonedUploadAge: java.time.Duration,
        @DefaultValue("image/jpeg,image/png,image/webp") val allowedMimeTypes: List<String>,
    )
}
