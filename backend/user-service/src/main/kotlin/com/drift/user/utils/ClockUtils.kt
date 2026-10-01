package com.drift.user.utils

import java.time.Clock
import java.time.Instant

object ClockUtils {
    fun now(clock: Clock): Instant = clock.instant()

    fun plusFrom(
        clock: Clock,
        duration: java.time.Duration,
    ): Instant = clock.instant().plus(duration)
}
