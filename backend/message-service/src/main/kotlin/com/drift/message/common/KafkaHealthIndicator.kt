package com.drift.message.common

import org.apache.kafka.clients.admin.AdminClient
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaAdmin
import java.util.concurrent.TimeUnit

/**
 * Readiness check for Kafka (spec section 14): healthy when a broker answers a cluster-description request within a
 * couple of seconds. [probe] returns how many brokers answered, so the logic can be tested without Kafka.
 */
class KafkaHealthIndicator(
    private val probe: () -> Int,
) : HealthIndicator {
    override fun health(): Health =
        try {
            val brokers = probe()
            if (brokers > 0) Health.up().withDetail("brokers", brokers).build() else Health.down().withDetail("brokers", 0).build()
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Health.down(e).build()
        }
}

@Configuration
class KafkaHealthConfig {
    @Bean
    fun kafkaHealthIndicator(admin: KafkaAdmin): KafkaHealthIndicator {
        // One long-lived admin client, created on the first check
        val client by lazy { AdminClient.create(admin.configurationProperties) }
        return KafkaHealthIndicator {
            client
                .describeCluster()
                .nodes()
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .size
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 2L
    }
}
