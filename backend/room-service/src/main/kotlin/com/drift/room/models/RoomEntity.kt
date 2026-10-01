package com.drift.room.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "rooms")
class RoomEntity(
    @Id
    @Column(name = "id")
    var id: UUID,
    @Column(name = "name", nullable = false)
    var name: String,
    @Column(name = "slug", nullable = false, unique = true)
    var slug: String,
    @Column(name = "created_by")
    var createdBy: UUID?,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
    @Column(name = "last_active_at", nullable = false)
    var lastActiveAt: Instant,
    @Column(name = "empty_since")
    var emptySince: Instant?,
) {
    fun toRoom(): Room = Room(id, name, slug, createdBy, createdAt, lastActiveAt, emptySince)

    override fun equals(other: Any?): Boolean = other is RoomEntity && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "RoomEntity(id=$id, slug=$slug)"
}
