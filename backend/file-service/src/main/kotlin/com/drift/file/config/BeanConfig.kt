package com.drift.file.config

import com.drift.file.common.JwtSupport
import io.minio.MinioAsyncClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Shared bean wiring: Clock (testable time), JwtSupport and the MinIO client. */
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

    @Bean
    fun minioClient(properties: AppProperties): MinioAsyncClient =
        MinioAsyncClient
            .builder()
            .endpoint(properties.storage.minioEndpoint)
            .credentials(properties.storage.minioAccessKey, properties.storage.minioSecretKey)
            .build()
}
