package com.drift.room.models

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class RoomMemberId(
    @Column(name = "room_id") var roomId: UUID,
    @Column(name = "user_id") var userId: UUID,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

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
