package ru.itmo.aiex.common.time

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

fun Clock.nowMicros(): Instant = Instant.now(this).truncatedTo(ChronoUnit.MICROS)
