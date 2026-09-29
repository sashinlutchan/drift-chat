package com.drift.user.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.health.contributor.Status

class KafkaHealthIndicatorTest {
    @Test
    fun `up when a broker answers`() {
        val health = KafkaHealthIndicator { 3 }.health()

        assertThat(health.status).isEqualTo(Status.UP)
        assertThat(health.details).containsEntry("brokers", 3)
    }

    @Test
    fun `down when no broker answers`() {
        assertThat(KafkaHealthIndicator { 0 }.health().status).isEqualTo(Status.DOWN)
    }

    @Test
    fun `down, not an exception, when the probe fails or times out`() {
        val health = KafkaHealthIndicator { throw java.util.concurrent.TimeoutException("no brokers") }.health()

        assertThat(health.status).isEqualTo(Status.DOWN)
    }
}
