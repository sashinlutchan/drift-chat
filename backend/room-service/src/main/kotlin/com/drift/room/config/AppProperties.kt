package com.drift.room.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.time.Duration

/** Typed, validated configuration for room-service (spec section 20). */
@ConfigurationProperties("drift")
data class AppProperties(
    @field:NotBlank val jwtSecret: String,
    @DefaultValue("drift_session") val sessionCookieName: String,
    @DefaultValue("24h") val roomIdleTtl: Duration,
    @DefaultValue("5m") val cleanupInterval: Duration,
    @NotBlank val userServiceAddr: String,
    @NotBlank val publicBaseUrl: String,
)
