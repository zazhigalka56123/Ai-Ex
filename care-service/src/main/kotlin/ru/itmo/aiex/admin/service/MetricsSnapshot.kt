package ru.itmo.aiex.admin.service

import java.time.Instant
import java.util.SortedMap

data class MetricsSnapshot(val metrics: SortedMap<String, Long>, val generatedAt: Instant)
