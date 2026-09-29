package com.drift.room.service

import com.drift.room.interfaces.IRoomService
import io.github.oshai.kotlinlogging.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/**
 * Scheduled cleanup (spec sections 8.1 and 13.2): deletes rooms nobody has been inside for `ROOM_IDLE_TTL`.
 * ShedLock makes sure only one replica runs it per interval. Contains no logic of its own.
 */
@Component
class RoomCleanupJob(
    private val rooms: IRoomService,
) {
    @Scheduled(fixedDelayString = "\${drift.cleanup-interval:5m}", initialDelayString = "\${drift.cleanup-initial-delay:1m}")
    @SchedulerLock(name = "room-cleanup", lockAtMostFor = "PT4M", lockAtLeastFor = "\${drift.cleanup-lock-at-least:PT1M}")
    fun cleanup() {
        val deleted = rooms.deleteIdleRooms()
        if (deleted > 0) log.info { "Deleted $deleted idle rooms" }
    }
}
