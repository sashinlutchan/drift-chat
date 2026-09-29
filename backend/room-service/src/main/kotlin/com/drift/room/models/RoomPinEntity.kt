package com.drift.room.models

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

/** Composite key (user_id, room_id). */
@Embeddable
data class RoomPinId(
    @Column(name = "user_id") var userId: UUID,
    @Column(name = "room_id") var roomId: UUID,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/** JPA entity for `room_pins` (spec section 18): pins are personal to each user. */
@Entity
@Table(name = "room_pins")
class RoomPinEntity(
    @EmbeddedId
    var id: RoomPinId,
    @Column(name = "pinned_at", nullable = false)
    var pinnedAt: Instant,
) {
    override fun equals(other: Any?): Boolean = other is RoomPinEntity && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "RoomPinEntity(id=$id)"
}
