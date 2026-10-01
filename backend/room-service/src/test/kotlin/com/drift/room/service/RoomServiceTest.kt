package com.drift.room.service

import com.drift.room.common.EventEnvelope
import com.drift.room.config.AppProperties
import com.drift.room.exceptions.DomainError
import com.drift.room.interfaces.IEventPublisher
import com.drift.room.interfaces.IUserDirectory
import com.drift.room.models.MembershipChange
import com.drift.room.models.NewRoom
import com.drift.room.models.Room
import com.drift.room.models.UserProfile
import com.drift.room.repository.IRoomRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class RoomServiceTest {
    private val now = Instant.parse("2026-06-01T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repository = mockk<IRoomRepository>()
    private val users = mockk<IUserDirectory>()
    private val events = mockk<IEventPublisher>(relaxed = true)
    private val properties =
        AppProperties("x".repeat(32), "drift_session", Duration.ofHours(24), Duration.ofMinutes(5), "u:1", "https://drift.test")
    private val meters =
        io.micrometer.core.instrument.simple
            .SimpleMeterRegistry()
    private val service = RoomService(repository, users, events, properties, clock, meters)

    private val me = UUID.randomUUID()
    private val other = UUID.randomUUID()

    private fun room(
        name: String = "Room",
        lastActive: Instant = now,
        emptySince: Instant? = null,
        createdBy: UUID? = me,
    ) = Room(UUID.randomUUID(), name, name.lowercase() + "-abc123", createdBy, now.minusSeconds(1000), lastActive, emptySince)

    private fun stubSummaryData(
        rooms: List<Room>,
        members: Map<UUID, List<UUID>> = emptyMap(),
        joined: Set<UUID> = emptySet(),
        left: Set<UUID> = emptySet(),
        pins: Map<UUID, Instant> = emptyMap(),
        online: Map<UUID, Boolean> = emptyMap(),
    ) {
        every { repository.memberIds(rooms.map { it.id }) } returns members
        every { repository.joinedRooms(me, rooms.map { it.id }) } returns joined
        every { repository.leftRooms(me, rooms.map { it.id }) } returns left
        every { repository.pins(me) } returns pins
        every { users.presence(any()) } returns online
    }

    private fun profile(
        id: UUID,
        name: String,
        online: Boolean,
    ) = UserProfile(id, name.lowercase(), name, null, online)

    @Test
    fun `pinned rooms come first with the most recently pinned on top, then the rest by recent activity`() {
        val oldPinned = room("OldPin", lastActive = now.minusSeconds(500))
        val newPinned = room("NewPin", lastActive = now.minusSeconds(900))
        val busy = room("Busy", lastActive = now)
        val quiet = room("Quiet", lastActive = now.minusSeconds(3600))
        val all = listOf(quiet, oldPinned, busy, newPinned)
        every { repository.search(null) } returns all
        stubSummaryData(all, pins = mapOf(oldPinned.id to now.minusSeconds(100), newPinned.id to now.minusSeconds(10)))

        val result = service.list(me, null)

        assertThat(result.map { it.room.name }).containsExactly("NewPin", "OldPin", "Busy", "Quiet")
        assertThat(result.take(2)).allMatch { it.pinnedAt != null }
    }

    @Test
    fun `cards carry member count, online count and the joined flag`() {
        val r = room("Chat")
        every { repository.search("cha") } returns listOf(r)
        stubSummaryData(
            listOf(r),
            members = mapOf(r.id to listOf(me, other)),
            joined = setOf(r.id),
            online =
                mapOf(
                    me to true,
                    other to false,
                ),
        )

        val card = service.list(me, "cha").single()

        assertThat(card.memberCount).isEqualTo(2)
        assertThat(card.onlineCount).isEqualTo(1)
        assertThat(card.joined).isTrue()
        assertThat(card.pinnedAt).isNull()
    }

    @Test
    fun `a room the user left shows as left so the card can offer Rejoin`() {
        val r = room("Left")
        every { repository.search(null) } returns listOf(r)
        stubSummaryData(listOf(r), left = setOf(r.id))

        val card = service.list(me, null).single()

        assertThat(card.left).isTrue()
        assertThat(card.joined).isFalse()
    }

    @Test
    fun `an unavailable user-service does not break the lobby, online just shows zero`() {
        val r = room()
        every { repository.search(null) } returns listOf(r)
        every { repository.memberIds(listOf(r.id)) } returns mapOf(r.id to listOf(me))
        every { repository.joinedRooms(me, any()) } returns setOf(r.id)
        every { repository.leftRooms(me, any()) } returns emptySet()
        every { repository.pins(me) } returns emptyMap()
        every { users.presence(any()) } throws DomainError.DependencyUnavailable("down")

        val card = service.list(me, null).single()

        assertThat(card.onlineCount).isZero()
        assertThat(card.memberCount).isEqualTo(1)
    }

    @Test
    fun `creating a room makes the creator a member and announces it`() {
        val saved = slot<NewRoom>()
        val created = room("Movies")
        every { repository.slugExists(any()) } returns false
        every { repository.save(capture(saved)) } returns created
        every { repository.join(created.id, me, now) } returns true
        stubSummaryData(listOf(created), members = mapOf(created.id to listOf(me)), joined = setOf(created.id))

        val summary = service.create(me, "  Movies  ")

        assertThat(saved.captured.name).isEqualTo("Movies")
        assertThat(saved.captured.slug).matches("movies-[a-z0-9]{6}")
        assertThat(saved.captured.createdBy).isEqualTo(me)
        assertThat(summary.joined).isTrue()
        verify { repository.join(created.id, me, now) }
        verify { events.publish("room.created", any<EventEnvelope<*>>()) }
        verify { events.publish("member.joined", any<EventEnvelope<*>>()) }
    }

    @Test
    fun `a taken slug is retried`() {
        val created = room()
        every { repository.slugExists(any()) } returnsMany listOf(true, true, false)
        every { repository.save(any()) } returns created
        every { repository.join(any(), any(), any()) } returns true
        stubSummaryData(listOf(created))

        service.create(me, "Movies")

        verify(exactly = 3) { repository.slugExists(any()) }
    }

    @Test
    fun `giving up on slugs is reported instead of looping forever`() {
        every { repository.slugExists(any()) } returns true

        assertThatThrownBy { service.create(me, "Movies") }.isInstanceOf(DomainError.ValidationFailed::class.java)
    }

    @Test
    fun `room names must be 1 to 50 characters`() {
        assertThatThrownBy { service.create(me, "   ") }.isInstanceOf(DomainError.ValidationFailed::class.java)
        assertThatThrownBy { service.create(me, "x".repeat(51)) }.isInstanceOf(DomainError.ValidationFailed::class.java)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `info shows creator and, for an empty room, when it will be deleted`() {
        val empty = room(emptySince = now.minus(Duration.ofHours(3)))
        every { repository.findById(empty.id) } returns empty
        stubSummaryData(listOf(empty))
        every { users.profiles(listOf(me)) } returns mapOf(me to profile(me, "Sipho", true))

        val info = service.info(empty.id, me)

        assertThat(info.createdBy?.name).isEqualTo("Sipho")
        assertThat(info.idleDeleteAt).isEqualTo(now.minus(Duration.ofHours(3)).plus(Duration.ofHours(24)))
    }

    @Test
    fun `info for a room whose creator was deleted has no creator, and an occupied room has no deletion time`() {
        val r = room(createdBy = null)
        every { repository.findById(r.id) } returns r
        stubSummaryData(listOf(r))

        val info = service.info(r.id, me)

        assertThat(info.createdBy).isNull()
        assertThat(info.idleDeleteAt).isNull()
    }

    @Test
    fun `info survives user-service being down`() {
        val r = room()
        every { repository.findById(r.id) } returns r
        stubSummaryData(listOf(r))
        every { users.profiles(any()) } throws DomainError.DependencyUnavailable("down")

        assertThat(service.info(r.id, me).createdBy).isNull()
    }

    @Test
    fun `unknown rooms are not found`() {
        every { repository.findById(any()) } returns null
        every { repository.findBySlug("nope") } returns null

        assertThatThrownBy { service.info(UUID.randomUUID(), me) }.isInstanceOf(DomainError.NotFound::class.java)
        assertThatThrownBy { service.bySlug("nope", me) }.hasMessageContaining("no longer exists")
        assertThatThrownBy { service.join(UUID.randomUUID(), me) }.isInstanceOf(DomainError.NotFound::class.java)
        assertThatThrownBy { service.members(UUID.randomUUID()) }.isInstanceOf(DomainError.NotFound::class.java)
    }

    @Test
    fun `resolving a share link returns the room card`() {
        val r = room("Link")
        every { repository.findBySlug(r.slug) } returns r
        stubSummaryData(listOf(r))

        assertThat(service.bySlug(r.slug, me).room).isEqualTo(r)
    }

    @Test
    fun `members list online people first, then offline, alphabetical inside each group`() {
        val r = room()
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val c = UUID.randomUUID()
        val d = UUID.randomUUID()
        every { repository.findById(r.id) } returns r
        every { repository.memberIds(listOf(r.id)) } returns mapOf(r.id to listOf(a, b, c, d))
        every { users.profiles(any()) } returns
            mapOf(
                a to profile(a, "zola", false),
                b to profile(b, "Bongi", true),
                c to profile(c, "amara", false),
                d to profile(d, "Thabo", true),
            )

        assertThat(service.members(r.id).map { it.name }).containsExactly("Bongi", "Thabo", "amara", "zola")
    }

    @Test
    fun `members who no longer exist as users are left out`() {
        val r = room()
        every { repository.findById(r.id) } returns r
        every { repository.memberIds(listOf(r.id)) } returns mapOf(r.id to listOf(me, other))
        every { users.profiles(any()) } returns mapOf(me to profile(me, "Me", true))

        assertThat(service.members(r.id)).hasSize(1)
    }

    @Test
    fun `joining announces the new member and the changed room`() {
        val r = room()
        every { repository.findById(r.id) } returns r
        every { repository.join(r.id, me, now) } returns true
        every { repository.memberCounts(listOf(r.id)) } returns mapOf(r.id to 2)
        stubSummaryData(listOf(r), joined = setOf(r.id))

        val summary = service.join(r.id, me)

        assertThat(summary.joined).isTrue()
        verify { events.publish("member.joined", any<EventEnvelope<*>>()) }
        verify { events.publish("room.updated", any<EventEnvelope<*>>()) }
    }

    @Test
    fun `joining a room you are already in is quiet`() {
        val r = room()
        every { repository.findById(r.id) } returns r
        every { repository.join(r.id, me, now) } returns false
        stubSummaryData(listOf(r), joined = setOf(r.id))

        service.join(r.id, me)

        verify(exactly = 0) { events.publish(any(), any<EventEnvelope<*>>()) }
    }

    @Test
    fun `leaving announces it, and leaving twice is an error`() {
        val r = room()
        every { repository.findById(r.id) } returns r
        every { repository.leave(r.id, me, now) } returnsMany listOf(true, false)
        every { repository.memberCounts(listOf(r.id)) } returns mapOf(r.id to 1)

        service.leave(r.id, me)
        verify { events.publish("member.left", any<EventEnvelope<*>>()) }
        assertThatThrownBy { service.leave(r.id, me) }.isInstanceOf(DomainError.NotFound::class.java)
    }

    @Test
    fun `pin and unpin are personal and need an existing room`() {
        val r = room()
        every { repository.findById(r.id) } returns r
        every { repository.findById(neq(r.id)) } returns null
        every { repository.pin(r.id, me, now) } just runs
        every { repository.unpin(r.id, me) } just runs

        service.pin(r.id, me)
        service.unpin(r.id, me)

        verify { repository.pin(r.id, me, now) }
        verify { repository.unpin(r.id, me) }
        assertThatThrownBy { service.pin(UUID.randomUUID(), me) }.isInstanceOf(DomainError.NotFound::class.java)
        assertThatThrownBy { service.unpin(UUID.randomUUID(), me) }.isInstanceOf(DomainError.NotFound::class.java)
    }

    @Test
    fun `isMember distinguishes member, non member and missing room`() {
        val r = room()
        every { repository.findById(r.id) } returns r
        every { repository.findById(neq(r.id)) } returns null
        every { repository.isMember(r.id, me) } returns true
        every { repository.isMember(r.id, other) } returns false

        assertThat(service.isMember(r.id, me)).isTrue()
        assertThat(service.isMember(r.id, other)).isFalse()
        assertThat(service.isMember(UUID.randomUUID(), me)).isNull()
        assertThat(service.find(r.id)).isEqualTo(r)
    }

    @Test
    fun `someone inside stops the countdown, an empty room starts it`() {
        val id = UUID.randomUUID()
        every { repository.markOccupied(id, now) } just runs
        every { repository.markEmpty(id, now) } just runs

        service.onOccupancy(id, 2)
        service.onOccupancy(id, 0)

        verify { repository.markOccupied(id, now) }
        verify { repository.markEmpty(id, now) }
    }

    @Test
    fun `cleanup deletes only rooms empty longer than the TTL and announces each`() {
        val threshold = slot<Instant>()
        val gone = listOf(room("A", emptySince = now.minus(Duration.ofHours(30))), room("B", emptySince = now.minus(Duration.ofHours(25))))
        every { repository.deleteEmptySince(capture(threshold)) } returns gone

        assertThat(service.deleteIdleRooms()).isEqualTo(2)

        assertThat(threshold.captured).isEqualTo(now.minus(Duration.ofHours(24)))
        verify(exactly = 2) { events.publish("room.deleted", any<EventEnvelope<*>>()) }
        assertThat(meters.counter("drift.rooms.deleted").count()).isEqualTo(2.0)
    }

    @Test
    fun `cleanup with nothing to delete announces nothing`() {
        every { repository.deleteEmptySince(any()) } returns emptyList()

        assertThat(service.deleteIdleRooms()).isZero()
        verify(exactly = 0) { events.publish(any(), any<EventEnvelope<*>>()) }
    }

    @Test
    fun `a deleted user leaves every room and other members are told`() {
        val r1 = room("One")
        val r2 = room("Two")
        every { repository.removeUser(me) } returns listOf(MembershipChange(r1.id, me), MembershipChange(r2.id, me))
        every { repository.findById(r1.id) } returns r1
        every { repository.findById(r2.id) } returns null
        every { repository.memberCounts(listOf(r1.id)) } returns mapOf(r1.id to 3)

        service.onUserDeleted(me)

        verify(exactly = 2) { events.publish("member.left", any<EventEnvelope<*>>()) }
        verify(exactly = 1) { events.publish("room.updated", any<EventEnvelope<*>>()) }
    }
}
