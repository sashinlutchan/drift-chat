package com.drift.message.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.UUID

/** Identifies this pod. Every pod reads Kafka under its own consumer group so each one can fan messages out to its own sockets. */
data class PodIdentity(
    val id: String,
)

@Configuration
class PodIdentityConfig {
    @Bean
    fun podIdentity(): PodIdentity = PodIdentity(System.getenv("HOSTNAME")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString())
}
