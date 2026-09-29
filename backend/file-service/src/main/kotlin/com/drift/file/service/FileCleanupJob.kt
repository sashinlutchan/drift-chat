package com.drift.file.service

import com.drift.file.config.AppProperties
import com.drift.file.interfaces.IFileService
import io.github.oshai.kotlinlogging.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/**
 * Removes records of uploads that were started but never finished (spec section 13.6: abandoned uploads). MinIO
 * itself drops the leftover parts; this clears the matching database rows. ShedLock keeps it to one replica at a time.
 */
@Component
class FileCleanupJob(
    private val files: IFileService,
    private val properties: AppProperties,
) {
    @Scheduled(fixedDelayString = "\${drift.cleanup-interval:1h}", initialDelayString = "\${drift.cleanup-initial-delay:10m}")
    @SchedulerLock(name = "file-cleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "\${drift.cleanup-lock-at-least:PT1M}")
    fun cleanup() {
        val removed = files.sweepAbandonedUploads(properties.storage.abandonedUploadAge)
        if (removed > 0) log.info { "Removed $removed abandoned upload records" }
    }
}
