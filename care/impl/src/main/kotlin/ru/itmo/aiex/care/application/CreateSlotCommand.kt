package ru.itmo.aiex.care.application

import java.time.Instant

data class CreateSlotCommand(val startsAt: Instant, val durationMin: Int)
