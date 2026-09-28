package com.drift.user

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName

/**
 * Boots the full application context against real infrastructure (Postgres, Kafka, Valkey via
 * Testcontainers) — proving Flyway migrations, JPA validation and configuration binding all work.
 * Real feature integration tests are added phase by phase.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class UserServiceIntegrationTest {
    companion object {
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:18"))

        private val kafka: ConfluentKafkaContainer =
            ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.1"))

        private val valkey: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("valkey/valkey:9-alpine"))
                .withExposedPorts(6379)
                .waitingFor(Wait.forListeningPort())

        @Container
        @ServiceConnection
        val postgresConnection: PostgreSQLContainer<*> = postgres

        @Container
        @ServiceConnection
        val kafkaConnection: ConfluentKafkaContainer = kafka

        @Container
        val valkeyContainer: GenericContainer<*> = valkey

        @DynamicPropertySource
        @JvmStatic
        fun valkeyProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { valkey.host }
            registry.add("spring.data.redis.port") { valkey.getMappedPort(6379).toString() }
        }
    }

    @Test
    fun `application context loads against real infrastructure`() {
        // Context loaded: Flyway ran, Hibernate validated the schema, Kafka/Valkey beans wired.
    }
}
