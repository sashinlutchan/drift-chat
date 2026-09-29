package com.drift.room.service

import com.drift.room.common.EventEnvelope
import com.drift.room.config.AppProperties
import com.drift.room.exceptions.DomainError
import com.drift.room.interfaces.IEventPublisher
import com.drift.room.interfaces.IRoomService
import com.drift.room.interfaces.IUserDirectory
import com.drift.room.models.MemberEventPayload
import com.drift.room.models.MemberProfile
import com.drift.room.models.NewRoom
import com.drift.room.models.Room
import com.drift.room.models.RoomDeletedPayload
import com.drift.room.models.RoomEventPayload
import com.drift.room.models.RoomInfo
import com.drift.room.models.RoomSummary
import com.drift.room.repository.IRoomRepository
import com.drift.room.utils.SlugGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Room business logic (spec sections 5–8 and 13.2). Talks only to the [IRoomRepository], [IUserDirectory] and
 * [IEventPublisher] ports (spec section 21 layering rule).
 */
@Service
class RoomService(
    private val repository: IRoomRepository,
    private val users: IUserDirectory,
    private val events: IEventPublisher,
    private val properties: AppProperties,
    private val clock: Clock,
    private val meters: io.micrometer.core.instrument.MeterRegistry,
) : IRoomService {
    @Transactional(readOnly = true)
    override fun list(
        userId: UUID,
        search: String?,
    ): List<RoomSummary> {
        val summaries = summarize(repository.search(search), userId)
        val (pinned, others) = summaries.partition { it.pinnedAt != null }
        return pinned.sortedByDescending { it.pinnedAt } + others.sortedByDescending { it.room.lastActiveAt }
    }

    @Transactional
    override fun create(
        userId: UUID,
        name: String,
    ): RoomSummary {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) {
            throw DomainError.ValidationFailed("Room name must be 1-$MAX_NAME_LENGTH characters.")
        }
        val room = repository.save(NewRoom(trimmed, uniqueSlug(trimmed), userId))
        repository.join(room.id, userId, clock.instant())
        publishRoom("room.created", EventEnvelope.Companion.Topics.ROOM_CREATED, room, 1)
        publishMember("member.joined", EventEnvelope.Companion.Topics.MEMBER_JOINED, room.id, userId)
        return summarize(listOf(room), userId).first()
    }

    @Transactional(readOnly = true)
    override fun info(
        roomId: UUID,
        userId: UUID,
    ): RoomInfo {
        val room = requireRoom(roomId)
        val summary = summarize(listOf(room), userId).first()
        val creator = room.createdBy?.let { runCatching { users.profiles(listOf(it))[it] }.getOrNull() }
        val idleDeleteAt = room.emptySince?.plus(properties.roomIdleTtl)
        return RoomInfo(summary, creator, idleDeleteAt)
    }

    @Transactional(readOnly = true)
    override fun bySlug(
        slug: String,
        userId: UUID,
    ): RoomSummary {
        val room = repository.findBySlug(slug) ?: throw DomainError.NotFound("This room no longer exists.")
        return summarize(listOf(room), userId).first()
    }

    @Transactional(readOnly = true)
    override fun members(roomId: UUID): List<MemberProfile> {
        requireRoom(roomId)
        val ids = repository.memberIds(listOf(roomId))[roomId].orEmpty()
        val profiles = users.profiles(ids)
        return ids
            .mapNotNull { profiles[it] }
            .map { MemberProfile(it.id, it.username, it.name, it.avatarFileId, it.online) }
            .sortedWith(compareByDescending<MemberProfile> { it.online }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    @Transactional
    override fun join(
        roomId: UUID,
        userId: UUID,
    ): RoomSummary {
        val room = requireRoom(roomId)
        if (repository.join(roomId, userId, clock.instant())) {
            publishMember("member.joined", EventEnvelope.Companion.Topics.MEMBER_JOINED, roomId, userId)
            publishRoom("room.updated", EventEnvelope.Companion.Topics.ROOM_UPDATED, room, memberCount(roomId))
        }
        return summarize(listOf(room), userId).first()
    }

    @Transactional
    override fun leave(
        roomId: UUID,
        userId: UUID,
    ) {
        val room = requireRoom(roomId)
        if (!repository.leave(roomId, userId, clock.instant())) throw DomainError.NotFound("You are not a member of this room.")
        publishMember("member.left", EventEnvelope.Companion.Topics.MEMBER_LEFT, roomId, userId)
        publishRoom("room.updated", EventEnvelope.Companion.Topics.ROOM_UPDATED, room, memberCount(roomId))
    }

    @Transactional
    override fun pin(
        roomId: UUID,
        userId: UUID,
    ) {
        requireRoom(roomId)
        repository.pin(roomId, userId, clock.instant())
    }

    @Transactional
    override fun unpin(
        roomId: UUID,
        userId: UUID,
    ) {
        requireRoom(roomId)
        repository.unpin(roomId, userId)
    }

    @Transactional(readOnly = true)
    override fun isMember(
        roomId: UUID,
        userId: UUID,
    ): Boolean? = repository.findById(roomId)?.let { repository.isMember(roomId, userId) }

    @Transactional(readOnly = true)
    override fun find(roomId: UUID): Room? = repository.findById(roomId)

    @Transactional
    override fun onOccupancy(
        roomId: UUID,
        count: Int,
    ) {
        if (count > 0) repository.markOccupied(roomId, clock.instant()) else repository.markEmpty(roomId, clock.instant())
    }

    @Transactional
    override fun onUserDeleted(userId: UUID) {
        val ended = repository.removeUser(userId)
        ended.forEach { change ->
            publishMember("member.left", EventEnvelope.Companion.Topics.MEMBER_LEFT, change.roomId, change.userId)
            repository.findById(change.roomId)?.let {
                publishRoom("room.updated", EventEnvelope.Companion.Topics.ROOM_UPDATED, it, memberCount(change.roomId))
            }
        }
    }

    @Transactional
    override fun deleteIdleRooms(): Int {
        val deleted = repository.deleteEmptySince(clock.instant().minus(properties.roomIdleTtl))
        deleted.forEach { room ->
            log.info { "Deleting idle room ${room.id} (${room.slug}), empty since ${room.emptySince}" }
            events.publish(
                EventEnvelope.Companion.Topics.ROOM_DELETED,
                EventEnvelope(type = "room.deleted", timestamp = clock.instant(), payload = RoomDeletedPayload(room.id.toString())),
            )
        }
        meters.counter("drift.rooms.deleted").increment(deleted.size.toDouble())
        return deleted.size
    }

    private fun requireRoom(id: UUID): Room = repository.findById(id) ?: throw DomainError.NotFound("This room no longer exists.")

    private fun memberCount(roomId: UUID): Int = repository.memberCounts(listOf(roomId))[roomId] ?: 0

    /** Adds counts, membership, pins and presence to plain rooms. Presence is best effort: a slow user-service must not break the lobby. */
    private fun summarize(
        rooms: List<Room>,
        userId: UUID,
    ): List<RoomSummary> {
        val ids = rooms.map { it.id }
        val memberIds = repository.memberIds(ids)
        val joined = repository.joinedRooms(userId, ids)
        val left = repository.leftRooms(userId, ids)
        val pins = repository.pins(userId)
        val online =
            runCatching { users.presence(memberIds.values.flatten()) }
                .onFailure { log.warn { "Presence unavailable, showing 0 online: ${it.message}" } }
                .getOrDefault(emptyMap())
        return rooms.map { room ->
            val members = memberIds[room.id].orEmpty()
            RoomSummary(room, members.size, members.count { online[it] == true }, room.id in joined, room.id in left, pins[room.id])
        }
    }

    private fun uniqueSlug(name: String): String {
        repeat(MAX_SLUG_ATTEMPTS) {
            val slug = SlugGenerator.generate(name)
            if (!repository.slugExists(slug)) return slug
        }
        throw DomainError.ValidationFailed("Could not create a unique link for this room. Please try again.")
    }

    private fun publishRoom(
        type: String,
        topic: String,
        room: Room,
        memberCount: Int,
    ) = events.publish(
        topic,
        EventEnvelope(
            type = type,
            timestamp = clock.instant(),
            payload = RoomEventPayload(room.id.toString(), room.name, room.slug, memberCount),
        ),
    )

    private fun publishMember(
        type: String,
        topic: String,
        roomId: UUID,
        userId: UUID,
    ) = events.publish(
        topic,
        EventEnvelope(type = type, timestamp = clock.instant(), payload = MemberEventPayload(roomId.toString(), userId.toString())),
    )

    private companion object {
        const val MAX_NAME_LENGTH = 50
        const val MAX_SLUG_ATTEMPTS = 5
    }
}
