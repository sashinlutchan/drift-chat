package com.drift.message.service

import com.drift.message.interfaces.IChatService
import io.github.oshai.kotlinlogging.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class OccupancySweeper(
    private val chat: IChatService,
) {
    @Scheduled(fixedDelayString = "\${drift.occupancy-sweep-interval:30s}", initialDelayString = "\${drift.occupancy-sweep-interval:30s}")
    @SchedulerLock(name = "occupancy-sweep", lockAtMostFor = "PT25S", lockAtLeastFor = "\${drift.occupancy-sweep-lock-at-least:PT0S}")
    fun sweep() {
        val changed = chat.sweepOccupancy()
        if (changed > 0) log.info { "Occupancy sweep changed $changed rooms" }
    }
}
