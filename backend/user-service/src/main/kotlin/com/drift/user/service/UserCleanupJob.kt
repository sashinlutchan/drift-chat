package com.drift.user.service

import com.drift.user.interfaces.IUserService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Scheduled cleanup (spec sections 13.1 and 20): deletes users whose session has
 * expired and publishes `user.deleted`. ShedLock guarantees only one replica runs
 * it per interval. Lives in the service layer; contains no logic of its own.
 */
@Component
class UserCleanupJob(
    private val userService: IUserService,
) {
    private val log = LoggerFactory.getLogger(UserCleanupJob::class.java)

    @Scheduled(fixedDelayString = "\${drift.cleanup-interval:5m}", initialDelayString = "\${drift.cleanup-initial-delay:1m}")
    @SchedulerLock(name = "user-cleanup", lockAtMostFor = "PT4M", lockAtLeastFor = "\${drift.cleanup-lock-at-least:PT1M}")
    fun cleanup() {
        val deleted = userService.deleteExpiredUsers()
        if (deleted.isNotEmpty()) {
            log.info("Deleted {} expired users", deleted.size)
        }
    }
}
