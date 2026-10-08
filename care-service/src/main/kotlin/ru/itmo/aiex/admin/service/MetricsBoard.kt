package ru.itmo.aiex.admin.service

import org.springframework.stereotype.Component
import ru.itmo.aiex.care.client.RemoteMetricsReader
import ru.itmo.aiex.common.metrics.MetricsContributor
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import java.time.Clock

@Component
class MetricsBoard(
    private val contributors: List<MetricsContributor>,
    private val clock: Clock,
    private val remoteMetrics: List<RemoteMetricsReader> = emptyList(),
) {
    fun snapshot(actor: Actor): MetricsSnapshot {
        actor.requireRole(RoleCode.ADMIN)
        val metrics = sortedMapOf<String, Long>()
        contributors.forEach { metrics.putAll(it.metrics()) }
        remoteMetrics.forEach { metrics.putAll(it.getMetrics()) }
        return MetricsSnapshot(metrics, clock.instant())
    }
}
