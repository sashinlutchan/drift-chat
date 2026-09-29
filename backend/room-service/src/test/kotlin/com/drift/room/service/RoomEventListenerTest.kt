package com.drift.room.service

import com.drift.room.common.EventEnvelope
import com.drift.room.common.EventEnvelopeJson
import com.drift.room.interfaces.IRoomService
import com.drift.room.models.RoomOccupancyPayload
import com.drift.room.models.UserDeletedPayload
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class RoomEventListenerTest {
    private val rooms = mockk<IRoomService>()
    private val listener = RoomEventListener(rooms)

    @Test
    fun `occupancy events drive the idle countdown`() {
        val roomId = UUID.randomUUID()
        every { rooms.onOccupancy(any(), any()) } just runs

        listener.onOccupancy(
            EventEnvelopeJson.serialize(
                EventEnvelope(type = "room.occupancy", timestamp = Instant.now(), payload = RoomOccupancyPayload(roomId.toString(), 3)),
            ),
        )
        listener.onOccupancy(
            EventEnvelopeJson.serialize(
                EventEnvelope(type = "room.occupancy", timestamp = Instant.now(), payload = RoomOccupancyPayload(roomId.toString(), 0)),
            ),
        )

        verify { rooms.onOccupancy(roomId, 3) }
        verify { rooms.onOccupancy(roomId, 0) }
    }

    @Test
    fun `user deleted removes their memberships`() {
        val userId = UUID.randomUUID()
        every { rooms.onUserDeleted(userId) } just runs

        listener.onUserDeleted(
            EventEnvelopeJson.serialize(
                EventEnvelope(type = "user.deleted", timestamp = Instant.now(), payload = UserDeletedPayload(userId.toString())),
            ),
        )

        verify { rooms.onUserDeleted(userId) }
    }
}
