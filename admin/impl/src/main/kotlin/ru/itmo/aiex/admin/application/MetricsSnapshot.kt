package ru.itmo.aiex.admin.application

import java.time.Instant
import java.util.SortedMap

data class MetricsSnapshot(val metrics: SortedMap<String, Long>, val generatedAt: Instant)
