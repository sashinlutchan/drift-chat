package com.drift.file.service

import com.drift.file.config.AppProperties
import com.drift.file.interfaces.IFileService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.util.unit.DataSize
import java.time.Duration

class FileCleanupJobTest {
    private val files = mockk<IFileService>()
    private val properties =
        AppProperties(
            jwtSecret = "x".repeat(32),
            sessionCookieName = "c",
            maxAvatarSize = DataSize.ofMegabytes(1),
            roomServiceAddr = "r",
            storage =
                AppProperties.Storage(
                    "http://m",
                    "a",
                    "b",
                    "av",
                    "me",
                    DataSize.ofMegabytes(1),
                    Duration.ofDays(3),
                    listOf("image/png"),
                ),
        )

    @Test
    fun `each run sweeps with the configured age`() {
        every { files.sweepAbandonedUploads(Duration.ofDays(3)) } returnsMany listOf(2, 0)
        val job = FileCleanupJob(files, properties)

        job.cleanup()
        job.cleanup()

        verify(exactly = 2) { files.sweepAbandonedUploads(Duration.ofDays(3)) }
    }
}
