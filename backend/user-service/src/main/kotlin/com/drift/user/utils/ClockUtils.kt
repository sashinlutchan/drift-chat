package com.drift.user.utils

import java.time.Clock
import java.time.Instant

/** Small time helpers around the injected [Clock] (spec section 21: injectable Clock). */
object ClockUtils {
    fun now(clock: Clock): Instant = clock.instant()

    fun plusFrom(
        clock: Clock,
        duration: java.time.Duration,
    ): Instant = clock.instant().plus(duration)
}
