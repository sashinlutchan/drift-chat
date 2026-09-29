package com.drift.file.service

import com.drift.file.common.EventEnvelope
import com.drift.file.common.EventEnvelopeJson
import com.drift.file.interfaces.IFileService
import com.drift.file.models.RoomDeletedPayload
import com.drift.file.models.UserDeletedPayload
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class FileEventListenerTest {
    private val files = mockk<IFileService>()
    private val listener = FileEventListener(files)

    @Test
    fun `room deleted removes the room's files`() {
        val roomId = UUID.randomUUID()
        every { files.deleteByRoom(roomId) } returns 3
        val json =
            EventEnvelopeJson.serialize(
                EventEnvelope(type = "room.deleted", timestamp = Instant.now(), payload = RoomDeletedPayload(roomId.toString())),
            )

        listener.onRoomDeleted(json)

        verify { files.deleteByRoom(roomId) }
    }

    @Test
    fun `user deleted removes the user's avatars`() {
        val userId = UUID.randomUUID()
        every { files.deleteAvatarsOwnedBy(userId) } returns 1
        val json =
            EventEnvelopeJson.serialize(
                EventEnvelope(type = "user.deleted", timestamp = Instant.now(), payload = UserDeletedPayload(userId.toString())),
            )

        listener.onUserDeleted(json)

        verify { files.deleteAvatarsOwnedBy(userId) }
    }

    @Test
    fun `replaying an event is harmless because nothing is left to delete`() {
        val roomId = UUID.randomUUID()
        every { files.deleteByRoom(roomId) } returnsMany listOf(2, 0)
        val json =
            EventEnvelopeJson.serialize(
                EventEnvelope(type = "room.deleted", timestamp = Instant.now(), payload = RoomDeletedPayload(roomId.toString())),
            )

        listener.onRoomDeleted(json)
        listener.onRoomDeleted(json)

        verify(exactly = 2) { files.deleteByRoom(roomId) }
    }
}
