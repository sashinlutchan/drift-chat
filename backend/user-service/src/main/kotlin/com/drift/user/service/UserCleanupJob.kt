package com.drift.user.service

import com.drift.user.interfaces.IUserService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class UserCleanupJob(
    private val userService: IUserService,
) {
    private val log = LoggerFactory.getLogger(UserCleanupJob::class.java)

    @Scheduled(fixedDelayString = "\${drift.cleanup-interval:5m}", initialDelayString = "\${drift.cleanup-initial-delay:1m}")
    @SchedulerLock(name = "user-cleanup", lockAtMostFor = "PT4M", lockAtLeastFor = "\${drift.cleanup-lock-at-least:PT1M}")
    fun cleanup() {
        val deleted = userService.deleteExpiredUsers()
        log.info("User cleanup ran: deleted {} expired users", deleted.size)
    }
}
