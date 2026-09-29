package com.drift.user

import com.drift.user.models.NewUser
import com.drift.user.repository.IUserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant

/**
 * Boots the full application context against real infrastructure (Postgres, Kafka, Valkey via
 * Testcontainers) — proving Flyway migrations, JPA validation and configuration binding all work.
 * Real feature integration tests are added phase by phase.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["drift.cleanup-interval=500ms", "drift.cleanup-initial-delay=0s", "drift.cleanup-lock-at-least=PT0S"],
)
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

    @Autowired lateinit var users: IUserRepository

    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `application context loads against real infrastructure`() {
        // Context loaded: Flyway ran, Hibernate validated the schema, Kafka/Valkey beans wired.
    }

    @Test
    fun `the scheduled cleanup really runs, deletes expired users and takes a ShedLock`() {
        val expired = users.save(NewUser(java.util.UUID.randomUUID(), "gone-user", "Gone", "g@b.co", null, Instant.now().minusSeconds(60)))
        val active = users.save(NewUser(java.util.UUID.randomUUID(), "still-here", "Here", "h@b.co", null, Instant.now().plusSeconds(3600)))

        val deadline = System.currentTimeMillis() + 15_000
        while (users.findById(expired.id) != null && System.currentTimeMillis() < deadline) Thread.sleep(200)

        assertThat(users.findById(expired.id)).isNull()
        assertThat(users.findById(active.id)).isNotNull()
        assertThat(jdbc.queryForObject("select count(*) from shedlock where name = 'user-cleanup'", Long::class.java)).isEqualTo(1L)
    }
}
