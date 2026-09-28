package com.drift.user.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.time.Duration

/** Typed, validated configuration for user-service (spec section 20). */
@ConfigurationProperties("drift")
data class AppProperties(
    @field:NotBlank val jwtSecret: String,
    @DefaultValue("drift_session") val sessionCookieName: String,
    @DefaultValue("true") val cookieSecure: Boolean,
    @DefaultValue("24h") val sessionTtl: Duration,
    @DefaultValue("60s") val presenceTtl: Duration,
    @DefaultValue("5m") val cleanupInterval: Duration,
    @DefaultValue("5MB") val maxAvatarSize: String,
    @NotBlank val fileServiceAddr: String,
)
