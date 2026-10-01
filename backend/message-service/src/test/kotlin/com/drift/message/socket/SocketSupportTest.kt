package com.drift.message.socket

import com.drift.message.TestSupport
import com.drift.message.common.JwtSupport
import com.drift.message.dto.MessageDto
import com.drift.message.dto.SenderDto
import com.drift.message.dto.SocketErrorDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SocketAuthenticatorTest {
    private val jwt = JwtSupport(TestSupport.properties().jwtSecret.toByteArray(), Clock.systemUTC())
    private val auth =
        SocketAuthenticator(jwt, TestSupport.properties(allowedOrigins = listOf("https://drift.test", "http://localhost:5173/")))
    private val userId = UUID.randomUUID()

    private fun token(ttl: Duration = Duration.ofHours(1)) = jwt.issue(userId.toString(), "sipho", ttl).serialize()

    @Test
    fun `an allowed origin with a valid session cookie is accepted, with the session's expiry`() {
        val result = auth.authenticate("https://drift.test", "theme=dark; drift_session=${token()}; other=1")

        assertThat(result).isInstanceOf(SocketAuth.Accepted::class.java)
        result as SocketAuth.Accepted
        assertThat(result.userId).isEqualTo(userId)
        assertThat(result.expiresAt).isAfter(Instant.now().plusSeconds(3000))
    }

    @Test
    fun `origins are compared without a trailing slash`() {
        assertThat(auth.authenticate("http://localhost:5173", "drift_session=${token()}")).isInstanceOf(SocketAuth.Accepted::class.java)
    }

    @Test
    fun `a foreign or missing origin is rejected even with a valid cookie (cross-site websocket hijacking)`() {
        val cookie = "drift_session=${token()}"

        assertThat(auth.authenticate("https://evil.example", cookie)).isEqualTo(SocketAuth.Rejected("origin not allowed"))
        assertThat(auth.authenticate(null, cookie)).isEqualTo(SocketAuth.Rejected("origin not allowed"))
    }

    @Test
    fun `no cookie, a tampered token and an expired token are rejected`() {
        assertThat(auth.authenticate("https://drift.test", null)).isEqualTo(SocketAuth.Rejected("no session cookie"))
        assertThat(auth.authenticate("https://drift.test", "other=1")).isEqualTo(SocketAuth.Rejected("no session cookie"))
        assertThat(auth.authenticate("https://drift.test", "drift_session=garbage")).isInstanceOf(SocketAuth.Rejected::class.java)
        assertThat(
            auth.authenticate("https://drift.test", "drift_session=${token(Duration.ofSeconds(-5))}"),
        ).isInstanceOf(SocketAuth.Rejected::class.java)
    }

    @Test
    fun `a token whose subject is not a user id is rejected`() {
        val bad = jwt.issue("not-a-uuid", "x", Duration.ofHours(1)).serialize()

        assertThat(auth.authenticate("https://drift.test", "drift_session=$bad")).isEqualTo(SocketAuth.Rejected("invalid session"))
    }
}

class SocketPayloadsTest {
    @Test
    fun `dtos become plain maps with string ids and iso timestamps`() {
        val id = UUID.randomUUID()
        val dto =
            MessageDto(
                id,
                UUID.randomUUID(),
                "text",
                "hi",
                null,
                SenderDto(UUID.randomUUID(), "Sipho", "sipho", null, false),
                Instant.parse("2026-06-01T12:00:00Z"),
            )

        val wire = SocketPayloads.toWire(dto) as Map<*, *>

        assertThat(wire["id"]).isEqualTo(id.toString())
        assertThat(wire["createdAt"]).isEqualTo("2026-06-01T12:00:00Z")
        assertThat((wire["sender"] as Map<*, *>)["name"]).isEqualTo("Sipho")
    }

    @Test
    fun `dtos nested inside maps are converted too, and Unit and null become null`() {
        val error = SocketErrorDto("room:enter", "FORBIDDEN", "Join this room first.")

        val wire = SocketPayloads.toWire(mapOf("ok" to false, "error" to error, "data" to null)) as Map<*, *>

        assertThat((wire["error"] as Map<*, *>)["code"]).isEqualTo("FORBIDDEN")
        assertThat(wire["ok"]).isEqualTo(false)
        assertThat(wire.containsKey("data")).isTrue()
    }

    @Test
    fun `maps and primitives keep their values`() {
        val map = mapOf("a" to 1)
        assertThat(SocketPayloads.toWire(map)).isEqualTo(map)
        assertThat(SocketPayloads.toWire("x")).isEqualTo("x")
        assertThat(SocketPayloads.toWire(5)).isEqualTo(5)
        assertThat(SocketPayloads.toWire(true)).isEqualTo(true)
    }
}

class SocketDispatcherTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dispatcher = SocketDispatcher(scope)

    @AfterEach
    fun stop() = scope.cancel()

    @Test
    fun `one socket's tasks run in the order they were submitted, even when earlier ones are slow`() {
        val order = CopyOnWriteArrayList<Int>()
        val done = CountDownLatch(20)

        (1..20).forEach { n ->
            dispatcher.submit("s1") {
                if (n % 5 == 0) kotlinx.coroutines.delay(20)
                order += n
                done.countDown()
            }
        }

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue()
        assertThat(order).containsExactlyElementsOf((1..20).toList())
    }

    @Test
    fun `different sockets run in parallel, a slow one does not hold up another`() {
        val fastDone = CountDownLatch(1)
        val release = CountDownLatch(1)
        dispatcher.submit("slow") { release.await(5, TimeUnit.SECONDS) }
        dispatcher.submit("fast") { fastDone.countDown() }

        assertThat(fastDone.await(2, TimeUnit.SECONDS)).isTrue()
        release.countDown()
    }

    @Test
    fun `a failing task is logged and the queue keeps going`() {
        val after = CountDownLatch(1)
        dispatcher.submit("s1") { error("boom") }
        dispatcher.submit("s1") { after.countDown() }

        assertThat(after.await(5, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `closing lets queued work finish first`() {
        val ran = CountDownLatch(1)
        dispatcher.submit("s1") {
            kotlinx.coroutines.delay(50)
            ran.countDown()
        }
        dispatcher.close("s1")

        assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue()
    }
}
