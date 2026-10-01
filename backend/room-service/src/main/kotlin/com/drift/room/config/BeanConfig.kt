package com.drift.room.config

import com.drift.room.common.JwtSupport
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@EnableConfigurationProperties(AppProperties::class)
class BeanConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun jwtSupport(
        properties: AppProperties,
        clock: Clock,
    ): JwtSupport = JwtSupport(properties.jwtSecret.toByteArray(), clock)
}
