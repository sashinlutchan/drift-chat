package com.drift.room.service

import com.drift.room.interfaces.IRoomService
import io.github.oshai.kotlinlogging.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class RoomCleanupJob(
    private val rooms: IRoomService,
) {
    @Scheduled(fixedDelayString = "\${drift.cleanup-interval:5m}", initialDelayString = "\${drift.cleanup-initial-delay:1m}")
    @SchedulerLock(name = "room-cleanup", lockAtMostFor = "PT4M", lockAtLeastFor = "\${drift.cleanup-lock-at-least:PT1M}")
    fun cleanup() {
        val deleted = rooms.deleteIdleRooms()
        log.info { "Room cleanup ran: deleted $deleted idle rooms" }
    }
}
