package com.drift.room.models

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

/** Composite key (room_id, user_id). */
@Embeddable
data class RoomMemberId(
    @Column(name = "room_id") var roomId: UUID,
    @Column(name = "user_id") var userId: UUID,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/** JPA entity for `room_members` (spec section 18); `leftAt == null` means the user is currently a member. */
@Entity
@Table(name = "room_members")
class RoomMemberEntity(
    @EmbeddedId
    var id: RoomMemberId,
    @Column(name = "joined_at", nullable = false)
    var joinedAt: Instant,
    @Column(name = "left_at")
    var leftAt: Instant?,
) {
    override fun equals(other: Any?): Boolean = other is RoomMemberEntity && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "RoomMemberEntity(id=$id)"
}
