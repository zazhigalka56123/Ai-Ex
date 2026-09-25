package ru.itmo.aiex.care.service

import java.time.Instant
import java.time.temporal.ChronoUnit

object SlotSchedule {
    const val MIN_DURATION_MIN = 15
    const val MAX_DURATION_MIN = 240

    fun end(start: Instant, durationMin: Int): Instant = start.plus(durationMin.toLong(), ChronoUnit.MINUTES)

    fun overlaps(aStart: Instant, aDurationMin: Int, bStart: Instant, bDurationMin: Int): Boolean =
        aStart < end(bStart, bDurationMin) && bStart < end(aStart, aDurationMin)

    fun candidatesFrom(start: Instant): Instant = start.minus(MAX_DURATION_MIN.toLong(), ChronoUnit.MINUTES)
}
