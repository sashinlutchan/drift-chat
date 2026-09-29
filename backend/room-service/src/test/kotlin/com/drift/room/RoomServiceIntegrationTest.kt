package com.drift.room

import com.drift.room.common.EventEnvelope
import com.drift.room.common.EventEnvelopeJson
import com.drift.room.interfaces.IRoomService
import com.drift.room.interfaces.IUserDirectory
import com.drift.room.models.RoomOccupancyPayload
import com.drift.room.models.UserDeletedPayload
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.UUID

/**
 * Real Postgres + Kafka: proves the JPA mapping and Flyway schema, pin ordering, join/rejoin, the idle-room
 * countdown driven by Kafka `room.occupancy` events, the ShedLock-guarded cleanup job, and user-deleted cascades.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "drift.cleanup-interval=500ms",
        "drift.cleanup-initial-delay=0s",
        "drift.cleanup-lock-at-least=PT0S",
        "drift.room-idle-ttl=24h",
    ],
)
@Testcontainers
class RoomServiceIntegrationTest {
    companion object {
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:18"))

        @Container
        @ServiceConnection
        val kafka: ConfluentKafkaContainer = ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.1"))
    }

    @Autowired lateinit var rooms: IRoomService

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @MockkBean lateinit var users: IUserDirectory

    private val me = UUID.randomUUID()
    private val friend = UUID.randomUUID()

    @BeforeEach
    fun stubUsers() {
        every { users.presence(any()) } answers { firstArg<Collection<UUID>>().associateWith { it == me } }
    }

    private fun waitFor(
        seconds: Long = 20,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + seconds * 1000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(200)
    }

    private fun event(
        topic: String,
        payload: Any,
    ) {
        kafkaTemplate
            .send(
                topic,
                EventEnvelopeJson.serialize(EventEnvelope(type = topic, timestamp = Instant.now(), payload = payload)),
            ).get()
    }

    private fun emptySince(id: UUID): Instant? =
        jdbc.queryForObject("select empty_since from rooms where id = ?", java.sql.Timestamp::class.java, id)?.toInstant()

    @Test
    fun `create, list, join, rejoin and leave against a real database`() {
        val created = rooms.create(me, "Integration Room")
        assertThat(created.room.slug).startsWith("integration-room-")
        assertThat(created.joined).isTrue()
        assertThat(created.memberCount).isEqualTo(1)
        assertThat(created.onlineCount).isEqualTo(1)

        // A second user sees it, isn't a member, can search for it, then joins.
        val seen = rooms.list(friend, "integration").single { it.room.id == created.room.id }
        assertThat(seen.joined).isFalse()
        assertThat(rooms.join(created.room.id, friend).memberCount).isEqualTo(2)
        assertThat(rooms.isMember(created.room.id, friend)).isTrue()

        // Leaving keeps the row but ends membership; joining again is a rejoin.
        rooms.leave(created.room.id, friend)
        assertThat(rooms.isMember(created.room.id, friend)).isFalse()
        assertThat(rooms.list(friend, "integration").single { it.room.id == created.room.id }.memberCount).isEqualTo(1)
        assertThat(rooms.list(friend, "integration").single { it.room.id == created.room.id }.left).isTrue()
        assertThat(rooms.join(created.room.id, friend).joined).isTrue()

        assertThat(rooms.bySlug(created.room.slug, me).room.id).isEqualTo(created.room.id)
    }

    @Test
    fun `pins are personal and float rooms to the top, most recently pinned first`() {
        val a = rooms.create(me, "Pin A").room
        val b = rooms.create(me, "Pin B").room
        val c = rooms.create(me, "Pin C").room
        rooms.pin(a.id, me)
        Thread.sleep(20)
        rooms.pin(b.id, me)

        val mine = rooms.list(me, "Pin ").map { it.room.name }
        assertThat(mine.take(3)).containsExactly("Pin B", "Pin A", "Pin C")
        assertThat(rooms.list(friend, "Pin ").none { it.pinnedAt != null }).isTrue() // pins are per user

        rooms.unpin(b.id, me)
        assertThat(
            rooms
                .list(me, "Pin ")
                .first()
                .room.name,
        ).isEqualTo("Pin A")
        assertThat(c.name).isEqualTo("Pin C")
    }

    @Test
    fun `occupancy events from Kafka start and stop the idle countdown`() {
        val room = rooms.create(me, "Countdown").room
        assertThat(emptySince(room.id)).isNotNull() // brand new rooms start their countdown

        event("room.occupancy", RoomOccupancyPayload(room.id.toString(), 2))
        waitFor { emptySince(room.id) == null }
        assertThat(emptySince(room.id)).isNull()

        event("room.occupancy", RoomOccupancyPayload(room.id.toString(), 0))
        waitFor { emptySince(room.id) != null }
        assertThat(emptySince(room.id)).isNotNull()
    }

    @Test
    fun `the scheduled cleanup deletes rooms empty for longer than the TTL and takes a ShedLock`() {
        val stale = rooms.create(me, "Stale Room").room
        val fresh = rooms.create(me, "Fresh Room").room
        jdbc.update("update rooms set empty_since = now() - interval '2 days' where id = ?", stale.id)
        jdbc.update("update rooms set empty_since = null where id = ?", fresh.id)

        waitFor { rooms.find(stale.id) == null }

        assertThat(rooms.find(stale.id)).isNull()
        assertThat(rooms.find(fresh.id)).isNotNull()
        assertThat(jdbc.queryForObject("select count(*) from shedlock where name = 'room-cleanup'", Long::class.java)).isEqualTo(1L)
        // Members and pins go with the room
        assertThat(jdbc.queryForObject("select count(*) from room_members where room_id = ?", Long::class.java, stale.id)).isZero()
    }

    @Test
    fun `a deleted user vanishes from rooms and pins and no longer counts as the creator`() {
        val room = rooms.create(friend, "Doomed Author").room
        rooms.join(room.id, me)
        rooms.pin(room.id, friend)

        event("user.deleted", UserDeletedPayload(friend.toString()))
        waitFor { rooms.isMember(room.id, friend) == false }

        assertThat(rooms.isMember(room.id, friend)).isFalse()
        assertThat(rooms.isMember(room.id, me)).isTrue()
        assertThat(jdbc.queryForObject("select count(*) from room_pins where user_id = ?", Long::class.java, friend)).isZero()
        assertThat(rooms.find(room.id)?.createdBy).isNull()
    }
}
