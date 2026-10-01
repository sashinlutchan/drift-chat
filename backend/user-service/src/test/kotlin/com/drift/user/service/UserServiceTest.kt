package com.drift.user.service

import com.drift.user.common.EventEnvelope
import com.drift.user.common.JwtSupport
import com.drift.user.config.AppProperties
import com.drift.user.exceptions.DomainError
import com.drift.user.interfaces.IAvatarUploader
import com.drift.user.interfaces.IEventPublisher
import com.drift.user.interfaces.IPresenceStore
import com.drift.user.models.NewUser
import com.drift.user.models.User
import com.drift.user.repository.IUserRepository
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@ExtendWith(MockKExtension::class)
class UserServiceTest {
    @MockK lateinit var userRepository: IUserRepository

    @MockK(relaxed = true)
    lateinit var presenceStore: IPresenceStore

    @MockK(relaxed = true)
    lateinit var avatarUploader: IAvatarUploader

    @MockK(relaxed = true)
    lateinit var eventPublisher: IEventPublisher

    private lateinit var userService: UserService

    private val fixedNow = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val jwtSupport = JwtSupport("test-secret-32-bytes-long!!!!!!!".toByteArray(), clock)

    private val props =
        AppProperties(
            jwtSecret = "test-secret-32-bytes-long!!!!!!!",
            sessionCookieName = "drift_session",
            cookieSecure = true,
            sessionTtl = Duration.ofHours(24),
            presenceTtl = Duration.ofSeconds(60),
            cleanupInterval = Duration.ofMinutes(5),
            maxAvatarSize = "5MB",
            fileServiceAddr = "localhost:9084",
        )

    private val meters =
        io.micrometer.core.instrument.simple
            .SimpleMeterRegistry()

    @BeforeEach
    fun setUp() {
        io.mockk.MockKAnnotations.init(this, relaxUnitFun = true)
        userService =
            UserService(userRepository, presenceStore, avatarUploader, eventPublisher, jwtSupport, props, clock, meters)
    }

    @Test
    fun `sign up validates username format`() {
        assertThatThrownBy {
            userService.signUp("x!", "Name", "a@b.co", "a.png", "image/png", stream(), 10L)
        }.isInstanceOf(DomainError.UsernameInvalid::class.java)
    }

    @Test
    fun `sign up rejects taken username`() {
        every { userRepository.existsByUsername("sipho") } returns true
        assertThatThrownBy {
            userService.signUp("sipho", "Sipho", "a@b.co", "a.png", "image/png", stream(), 10L)
        }.isInstanceOf(DomainError.UsernameTaken::class.java)
            .hasMessageContaining("This username is taken")
    }

    @Test
    fun `sign up rejects invalid email`() {
        every { userRepository.existsByUsername("sipho") } returns false
        assertThatThrownBy {
            userService.signUp("sipho", "Sipho", "not-an-email", "a.png", "image/png", stream(), 10L)
        }.isInstanceOf(DomainError.ValidationFailed::class.java)
    }

    @Test
    fun `sign up rejects unsupported avatar type`() {
        every { userRepository.existsByUsername("sipho") } returns false
        assertThatThrownBy {
            userService.signUp("sipho", "Sipho", "a@b.co", "a.gif", "image/gif", stream(), 10L)
        }.isInstanceOf(DomainError.UnsupportedMediaType::class.java)
    }

    @Test
    fun `sign up stores avatar saves user and issues jwt`() {
        every { userRepository.existsByUsername("sipho") } returns false
        val fileId = UUID.randomUUID()
        val ownerSlot: CapturingSlot<UUID> = slot()
        every { avatarUploader.uploadAvatar(capture(ownerSlot), any(), any(), any(), any()) } returns fileId
        val savedSlot: CapturingSlot<NewUser> = slot()
        every { userRepository.save(capture(savedSlot)) } answers {
            val nu = savedSlot.captured
            User(nu.id, nu.username, nu.name, nu.email, nu.avatarFileId, fixedNow, fixedNow, nu.expiresAt)
        }

        val result = userService.signUp("sipho", "Sipho", "a@b.co", "a.png", "image/png", stream(), 10L)

        assertThat(ownerSlot.captured).isEqualTo(result.user.id)

        assertThat(result.user.username).isEqualTo("sipho")
        assertThat(result.user.avatarFileId).isEqualTo(fileId)
        assertThat(result.user.expiresAt).isEqualTo(fixedNow.plus(Duration.ofHours(24)))
        assertThat(result.sessionJwt).isNotBlank()
        val claims = jwtSupport.verify(result.sessionJwt)
        assertThat(claims!!.getClaim("username")).isEqualTo("sipho")
    }

    @Test
    fun `sign up removes the stored avatar again if the user cannot be saved`() {
        every { userRepository.existsByUsername("sipho") } returns false
        val fileId = UUID.randomUUID()
        every { avatarUploader.uploadAvatar(any(), any(), any(), any(), any()) } returns fileId
        every { userRepository.save(any()) } throws IllegalStateException("db down")

        assertThatThrownBy { userService.signUp("sipho", "Sipho", "a@b.co", "a.png", "image/png", stream(), 10L) }
            .isInstanceOf(IllegalStateException::class.java)

        verify { avatarUploader.deleteAvatar(fileId) }
    }

    @Test
    fun `get by id throws NotFound when missing`() {
        every { userRepository.findById(any()) } returns null
        assertThatThrownBy { userService.getById(UUID.randomUUID()) }
            .isInstanceOf(DomainError.NotFound::class.java)
    }

    @Test
    fun `username available checks repository`() {
        every { userRepository.existsByUsername("free") } returns false
        every { userRepository.existsByUsername("taken") } returns true
        assertThat(userService.usernameAvailable("free")).isTrue()
        assertThat(userService.usernameAvailable("taken")).isFalse()
    }

    @Test
    fun `update profile replaces avatar and publishes user updated`() {
        val id = UUID.randomUUID()
        val existing = User(id, "sipho", "Old", "old@b.co", UUID.randomUUID(), fixedNow, fixedNow, fixedNow.plusSeconds(9))
        val fileId = UUID.randomUUID()
        every { userRepository.findById(id) } returns existing
        every { avatarUploader.uploadAvatar(any(), any(), any(), any(), any()) } returns fileId
        every {
            userRepository.updateProfile(id, "New", "old@b.co", fileId)
        } returns existing.copy(name = "New", avatarFileId = fileId)

        val updated = userService.updateProfile(id, "New", null, "n.png", "image/png", stream(), 5L)

        assertThat(updated.name).isEqualTo("New")
        assertThat(updated.avatarFileId).isEqualTo(fileId)
        verify { avatarUploader.deleteAvatar(existing.avatarFileId!!) }
        verify {
            eventPublisher.publish(
                EventEnvelope.Companion.Topics.USER_UPDATED,
                any<EventEnvelope<Any>>(),
            )
        }
    }

    @Test
    fun `heartbeat stores presence with ttl`() {
        userService.heartbeat(UUID.randomUUID())
        verify { presenceStore.heartbeat(any(), Duration.ofSeconds(60)) }
    }

    @Test
    fun `online status maps every id`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        every { presenceStore.isOnline(a) } returns true
        every { presenceStore.isOnline(b) } returns false
        assertThat(userService.onlineStatus(listOf(a, b))).containsExactlyInAnyOrderEntriesOf(mapOf(a to true, b to false))
    }

    @Test
    fun `cleanup deletes expired users publishes user deleted and clears presence`() {
        val expiredId = UUID.randomUUID()
        every { userRepository.deleteExpired(fixedNow) } returns listOf(expiredId)

        val deleted = userService.deleteExpiredUsers()

        assertThat(deleted).containsExactly(expiredId)
        assertThat(meters.counter("drift.users.expired").count()).isEqualTo(1.0)
        verify { presenceStore.remove(expiredId) }
        verify {
            eventPublisher.publish(
                EventEnvelope.Companion.Topics.USER_DELETED,
                any<EventEnvelope<Any>>(),
            )
        }
    }

    @Test
    fun `cleanup with no expired users publishes nothing`() {
        every { userRepository.deleteExpired(fixedNow) } returns listOf()
        userService.deleteExpiredUsers()
        verify(exactly = 0) { eventPublisher.publish<Any>(any(), any()) }
    }

    private fun stream() = ByteArrayInputStream(byteArrayOf(1, 2, 3))
}
