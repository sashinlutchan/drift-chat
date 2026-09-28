package com.drift.user.service

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
    private val userService: UserService,
) {
    private val log = LoggerFactory.getLogger(UserCleanupJob::class.java)

    @Scheduled(fixedDelayString = "\${CLEANUP_INTERVAL_MS:300000}", initialDelay = 60_000L)
    @SchedulerLock(name = "user-cleanup", lockAtMostFor = "PT4M", lockAtLeastFor = "PT1M")
    fun cleanup() {
        val deleted = userService.deleteExpiredUsers()
        if (deleted.isNotEmpty()) {
            log.info("Deleted {} expired users", deleted.size)
        }
    }
}
