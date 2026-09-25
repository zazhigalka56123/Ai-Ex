package ru.itmo.aiex.care.service

import java.time.Instant

data class CreateSlotCommand(val startsAt: Instant, val durationMin: Int)
