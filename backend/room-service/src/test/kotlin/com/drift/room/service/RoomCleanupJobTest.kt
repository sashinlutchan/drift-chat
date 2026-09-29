package com.drift.room.service

import com.drift.room.interfaces.IRoomService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class RoomCleanupJobTest {
    private val rooms = mockk<IRoomService>()

    @Test
    fun `each run asks the service to delete idle rooms`() {
        every { rooms.deleteIdleRooms() } returnsMany listOf(2, 0)
        val job = RoomCleanupJob(rooms)

        job.cleanup()
        job.cleanup()

        verify(exactly = 2) { rooms.deleteIdleRooms() }
    }
}
