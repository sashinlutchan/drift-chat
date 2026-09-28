package com.drift.user.config

import com.drift.user.common.JwtSupport
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Shared bean wiring: Clock (for testable TTL logic) and JwtSupport. */
@Configuration
@EnableConfigurationProperties(AppProperties::class)
class BeanConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun jwtSupport(
        @Value("\${JWT_SECRET:test-secret-key-that-is-32-bytes-long!}") secret: String,
        clock: Clock,
    ): JwtSupport = JwtSupport(secret.toByteArray(), clock)
}
