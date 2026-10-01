package com.drift.message

import com.drift.message.common.JwtSupport
import com.drift.message.interfaces.IFileStore
import com.drift.message.interfaces.IMessageService
import com.drift.message.interfaces.IRoomMembership
import com.drift.message.interfaces.IUploadService
import com.drift.message.interfaces.IUserDirectory
import com.drift.message.models.CompletedUpload
import com.drift.message.models.RoomAccess
import com.drift.message.models.StartedUpload
import com.drift.message.models.UserProfile
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.io.ByteArrayInputStream
import java.net.ServerSocket
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class MessageServiceIntegrationTest {
    companion object {
        private val socketPort: Int = ServerSocket(0).use { it.localPort }
        private const val ORIGIN = "https://drift.test"
        private const val SECRET = "test-secret-key-that-is-32-bytes-long!"

        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:18"))

        @Container
        @ServiceConnection
        val kafka: ConfluentKafkaContainer = ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.1"))

        @Container
        val valkey: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("valkey/valkey:9-alpine")).withExposedPorts(6379).waitingFor(Wait.forListeningPort())

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { valkey.host }
            registry.add("spring.data.redis.port") { valkey.getMappedPort(6379).toString() }
            registry.add("drift.jwt-secret") { SECRET }
            registry.add("drift.socket.port") { socketPort.toString() }
            registry.add("drift.socket.allowed-origins") { ORIGIN }
            registry.add("drift.upload-chunk-size") { "5MB" }
        }
    }

    @Autowired lateinit var messages: IMessageService

    @Autowired lateinit var uploads: IUploadService

    @Autowired lateinit var redis: StringRedisTemplate

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired lateinit var registry: KafkaListenerEndpointRegistry

    @MockkBean lateinit var rooms: IRoomMembership

    @MockkBean lateinit var users: IUserDirectory

    @MockkBean lateinit var files: IFileStore

    private val jwt = JwtSupport(SECRET.toByteArray(), Clock.systemUTC())
    private val room = UUID.randomUUID()
    private val sipho = UUID.randomUUID()
    private val thandi = UUID.randomUUID()
    private val stranger = UUID.randomUUID()
    private val clients = mutableListOf<SocketIoTestClient>()

    private fun cookie(user: UUID) = "drift_session=${jwt.issue(user.toString(), "u", Duration.ofHours(1)).serialize()}"

    private fun connect(user: UUID): SocketIoTestClient = SocketIoTestClient.connect(socketPort, ORIGIN, cookie(user)).also(clients::add)

    @BeforeEach
    fun setUp() {
        every { rooms.access(room, sipho) } returns RoomAccess.MEMBER
        every { rooms.access(room, thandi) } returns RoomAccess.MEMBER
        every { rooms.access(room, stranger) } returns RoomAccess.NOT_MEMBER
        every { users.setPresence(any(), any()) } returns Unit
        every { users.profiles(any()) } answers {
            firstArg<Collection<UUID>>().associateWith {
                UserProfile(it, if (it == sipho) "sipho" else "thandi", if (it == sipho) "Sipho" else "Thandi", null, true)
            }
        }
        waitForKafkaListeners()
    }

    @AfterEach
    fun tearDown() = clients.forEach { it.close() }

    private fun waitForKafkaListeners() {
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            val containers = registry.listenerContainers
            if (containers.isNotEmpty() && containers.all { !it.assignedPartitions.isNullOrEmpty() }) return
            Thread.sleep(250)
        }
        error("Kafka listeners never got their partitions")
    }

    private fun waitFor(
        seconds: Long = 20,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + seconds * 1000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        return condition()
    }

    private fun occupancy(): Long = redis.opsForZSet().zCard("room:occupancy:$room") ?: 0

    private fun send(
        client: SocketIoTestClient,
        text: String,
        clientId: String,
    ) = client.emit("message:send", mapOf("roomId" to room.toString(), "text" to text, "clientId" to clientId))

    @Test
    fun `handshakes are refused without a cookie, with a bad token or from a foreign origin`() {
        assertThatThrownBy { SocketIoTestClient.connect(socketPort, ORIGIN, null) }.isInstanceOf(Exception::class.java)
        assertThatThrownBy { SocketIoTestClient.connect(socketPort, ORIGIN, "drift_session=garbage") }.isInstanceOf(Exception::class.java)
        assertThatThrownBy {
            SocketIoTestClient.connect(
                socketPort,
                "https://evil.example",
                cookie(sipho),
            )
        }.isInstanceOf(Exception::class.java)
        assertThatThrownBy { SocketIoTestClient.connect(socketPort, null, cookie(sipho)) }.isInstanceOf(Exception::class.java)
    }

    @Test
    fun `a valid cookie and origin connects and marks the user online`() {
        connect(sipho)

        assertThat(waitFor { runCatching { verify { users.setPresence(sipho, true) } }.isSuccess }).isTrue()
    }

    @Test
    fun `a message is stored encrypted, acked to the sender and fanned out to everyone in the room via Kafka`() {
        val a = connect(sipho)
        val b = connect(thandi)
        assertThat(a.emit("room:enter", room.toString())["ok"].asBoolean()).isTrue()
        assertThat(b.emit("room:enter", room.toString())["ok"].asBoolean()).isTrue()

        val ack = send(a, "meet at 6 — top secret", "client-1")

        assertThat(ack["ok"].asBoolean()).isTrue()
        assertThat(ack["data"]["clientId"].asText()).isEqualTo("client-1")
        val id = ack["data"]["id"].asText()
        a.await("message:ack") { it["clientId"].asText() == "client-1" }

        val received = b.await("message:new") { it["id"].asText() == id }
        assertThat(received["text"].asText()).isEqualTo("meet at 6 — top secret")
        assertThat(received["sender"]["name"].asText()).isEqualTo("Sipho")
        assertThat(received["type"].asText()).isEqualTo("text")
        a.await("message:new") { it["id"].asText() == id }

        val stored = jdbc.queryForObject("select ciphertext from messages where id = ?", ByteArray::class.java, UUID.fromString(id))!!
        assertThat(String(stored, Charsets.ISO_8859_1)).doesNotContain("secret")
        assertThat(jdbc.queryForObject("select key_version from messages where id = ?", Int::class.java, UUID.fromString(id))).isEqualTo(1)

        val raw = readTopic("message.created") { it.contains(id) }
        assertThat(raw).isNotNull()
        assertThat(raw).doesNotContain("secret")
    }

    @Test
    fun `a retried clientId does not create a second message`() {
        val a = connect(sipho)
        a.emit("room:enter", room.toString())

        val first = send(a, "only once", "dup-1")["data"]["id"].asText()
        val second = send(a, "only once", "dup-1")["data"]["id"].asText()

        assertThat(second).isEqualTo(first)
        assertThat(
            jdbc.queryForObject("select count(*) from messages where room_id = ? and type = 'text'", Long::class.java, room),
        ).isEqualTo(1L)
    }

    @Test
    fun `sockets outside the room get nothing, and you cannot send before entering`() {
        val inside = connect(sipho)
        val outside = connect(thandi)
        inside.emit("room:enter", room.toString())

        val notInside = outside.emit("message:send", mapOf("roomId" to room.toString(), "text" to "hi", "clientId" to "x1"))
        assertThat(notInside["ok"].asBoolean()).isFalse()
        assertThat(notInside["error"]["code"].asText()).isEqualTo("FORBIDDEN")
        outside.await("error")

        send(inside, "for the room only", "y1")
        inside.await("message:new") { it["text"].asText() == "for the room only" }
        Thread.sleep(500)
        assertThat(outside.events.none { it.name == "message:new" }).isTrue()
    }

    @Test
    fun `non members cannot enter and bad input is answered with an error event`() {
        val s = connect(stranger)

        val denied = s.emit("room:enter", room.toString())
        assertThat(denied["ok"].asBoolean()).isFalse()
        assertThat(denied["error"]["code"].asText()).isEqualTo("FORBIDDEN")

        val badId = s.emit("room:enter", "not-a-uuid")
        assertThat(badId["error"]["code"].asText()).isEqualTo("VALIDATION_FAILED")
        assertThat(occupancy()).isZero()
    }

    @Test
    fun `empty and oversized messages are refused with a validation error`() {
        val a = connect(sipho)
        a.emit("room:enter", room.toString())

        assertThat(send(a, "   ", "e1")["error"]["code"].asText()).isEqualTo("VALIDATION_FAILED")
        assertThat(send(a, "x".repeat(2001), "e2")["error"]["message"].asText()).contains("2000")
    }

    @Test
    fun `typing shows for the others through Valkey pub-sub and clears when the user stops`() {
        val a = connect(sipho)
        val b = connect(thandi)
        a.emit("room:enter", room.toString())
        b.emit("room:enter", room.toString())

        a.emitNoAck("typing:start", room.toString())
        val typing = b.await("typing:update") { it["users"].size() == 1 }
        assertThat(typing["users"][0]["name"].asText()).isEqualTo("Sipho")

        a.emitNoAck("typing:stop", room.toString())
        b.await("typing:update") { it["users"].size() == 0 }
    }

    @Test
    fun `entering and leaving a room publishes occupancy and keeps Valkey in step`() {
        val a = connect(sipho)
        a.emit("room:enter", room.toString())
        assertThat(waitFor { occupancy() == 1L }).isTrue()
        assertThat(readTopic("room.occupancy") { it.contains(room.toString()) && it.contains("\"count\":1") }).isNotNull()

        a.emit("room:exit", room.toString())
        assertThat(waitFor { occupancy() == 0L }).isTrue()
        assertThat(readTopic("room.occupancy") { it.contains(room.toString()) && it.contains("\"count\":0") }).isNotNull()
    }

    @Test
    fun `closing the socket takes the user out of the room and marks them offline`() {
        val a = connect(sipho)
        a.emit("room:enter", room.toString())
        assertThat(waitFor { occupancy() == 1L }).isTrue()

        a.close()

        assertThat(waitFor { occupancy() == 0L }).isTrue()
        assertThat(waitFor { runCatching { verify { users.setPresence(sipho, false) } }.isSuccess }).isTrue()
    }

    @Test
    fun `a heartbeat with no payload is accepted and refreshes presence`() {
        val a = connect(sipho)
        a.emit("room:enter", room.toString())

        a.emitNoAck("presence:heartbeat")

        assertThat(waitFor { runCatching { verify(atLeast = 2) { users.setPresence(sipho, true) } }.isSuccess }).isTrue()
    }

    private fun publish(
        topic: String,
        payload: String,
    ) {
        val envelope = """{"id":"${UUID.randomUUID()}","type":"$topic","version":1,"timestamp":"${Instant.now()}","traceId":null"""
        val json = """$envelope,"payload":$payload}"""
        kafkaTemplate.send(topic, room.toString(), json).get()
    }

    @Test
    fun `lobby and presence events from Kafka reach connected sockets, the room ones only the room`() {
        val a = connect(sipho)
        val b = connect(thandi)
        a.emit("room:enter", room.toString())

        publish("presence.changed", """{"userId":"$thandi","online":false}""")
        publish("room.created", """{"roomId":"${UUID.randomUUID()}","name":"New","slug":"new-1","memberCount":1}""")
        publish("member.joined", """{"roomId":"$room","userId":"$thandi"}""")

        assertThat(a.await("presence:update")["online"].asBoolean()).isFalse()
        b.await("presence:update")
        b.await("room:created")
        assertThat(a.await("member:joined")["userId"].asText()).isEqualTo(thandi.toString())
        Thread.sleep(500)
        assertThat(b.count("member:joined")).isZero()
    }

    @Test
    fun `when somebody leaves the room the others hear it, they get an inline notice and their sockets stop receiving`() {
        val a = connect(sipho)
        val b = connect(thandi)
        a.emit("room:enter", room.toString())
        b.emit("room:enter", room.toString())
        assertThat(waitFor { occupancy() == 2L }).isTrue()

        publish("member.left", """{"roomId":"$room","userId":"$thandi"}""")

        a.await("member:left") { it["userId"].asText() == thandi.toString() }
        b.await("member:left")
        a.await("message:new") { it["type"].asText() == "system" && it["text"].asText() == "Thandi left the room" }
        assertThat(waitFor { occupancy() == 1L }).isTrue()
        val afterLeaving = b.emit("message:send", mapOf("roomId" to room.toString(), "text" to "still here?", "clientId" to "z1"))
        assertThat(afterLeaving["ok"].asBoolean()).isFalse()
    }

    @Test
    fun `a deleted room removes its messages and tells everyone`() {
        val a = connect(sipho)
        a.emit("room:enter", room.toString())
        send(a, "soon gone", "d1")
        a.await("message:new") { it["text"].asText() == "soon gone" }

        publish("room.deleted", """{"roomId":"$room"}""")

        a.await("room:deleted")
        assertThat(
            waitFor { jdbc.queryForObject("select count(*) from messages where room_id = ?", Long::class.java, room) == 0L },
        ).isTrue()
    }

    @Test
    fun `a deleted user's messages show Deleted user`() {
        val victim = UUID.randomUUID()
        every { rooms.access(room, victim) } returns RoomAccess.MEMBER
        messages.sendText(room, victim, "I was here", "v1")

        publish("user.deleted", """{"userId":"$victim"}""")

        assertThat(
            waitFor {
                jdbc.queryForObject("select count(*) from messages where room_id = ? and sender_deleted", Long::class.java, room) ==
                    1L
            },
        ).isTrue()
        val page = messages.history(sipho, room, null, 10)
        assertThat(page.messages.single { it.text == "I was here" }.senderDeleted).isTrue()
    }

    @Test
    fun `history pages backwards with a cursor and only for members`() {
        (1..7).forEach { messages.sendText(room, sipho, "message $it", "h$it") }

        val first = messages.history(sipho, room, null, 3)
        assertThat(first.messages.map { it.text }).containsExactly("message 7", "message 6", "message 5")
        val second = messages.history(sipho, room, first.nextCursor, 3)
        assertThat(second.messages.map { it.text }).containsExactly("message 4", "message 3", "message 2")
        val third = messages.history(sipho, room, second.nextCursor, 3)
        assertThat(third.messages.map { it.text }).containsExactly("message 1")
        assertThat(third.nextCursor).isNull()

        assertThatThrownBy { messages.history(stranger, room, null, 3) }.hasMessageContaining("Join this room")
    }

    @Test
    fun `an upload survives a dropped connection, resumes with the missing chunks and ends as a media message on the socket`() {
        val chunk = 5L * 1024 * 1024
        val size = chunk + 1_000_000
        val fileId = UUID.randomUUID()
        every { files.start(sipho, room, "holiday.mp4", "video/mp4", size) } returns StartedUpload(fileId, "mp-1")
        every { files.uploadPart(any(), any(), any(), any()) } returns "etag"
        every { files.complete(StartedUpload(fileId, "mp-1"), size) } returns CompletedUpload(fileId, "video/mp4", size)
        val watcher = connect(thandi)
        watcher.emit("room:enter", room.toString())

        val session = uploads.start(sipho, room, "holiday.mp4", size, "video/mp4")
        assertThat(session.totalChunks).isEqualTo(2)

        uploads.uploadChunk(sipho, session.id, 0, chunk, ByteArrayInputStream(ByteArray(0)))
        val (_, received) = uploads.status(sipho, session.id)
        assertThat(received).containsExactly(0)
        assertThatThrownBy { uploads.complete(sipho, session.id) }.hasMessageContaining("[1]")
        uploads.uploadChunk(sipho, session.id, 1, 1_000_000, ByteArrayInputStream(ByteArray(0)))

        val media = uploads.complete(sipho, session.id)

        assertThat(media.fileId).isEqualTo(fileId)
        val live = watcher.await("message:new") { it["type"].asText() == "video" }
        assertThat(live["fileId"].asText()).isEqualTo(fileId.toString())
        assertThat(live["text"].asText()).isEqualTo("holiday.mp4")
        assertThat(redis.hasKey("upload:${session.id}")).isFalse()
    }

    private fun readTopic(
        topic: String,
        matches: (String) -> Boolean,
    ): String? {
        val props =
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "test-${UUID.randomUUID()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            )
        KafkaConsumer<String, String>(props).use { consumer ->
            consumer.subscribe(listOf(topic))
            val deadline = System.currentTimeMillis() + 20_000
            while (System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach { if (matches(it.value())) return it.value() }
            }
        }
        return null
    }
}
