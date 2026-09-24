package ru.itmo.aiex.common.paging

import java.time.Instant
import java.util.UUID

data class TimeIdPosition(val timestamp: Instant, val id: UUID)
