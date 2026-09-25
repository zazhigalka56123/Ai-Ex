package ru.itmo.aiex.admin.service

import org.springframework.stereotype.Component
import ru.itmo.aiex.common.metrics.MetricsContributor
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import java.time.Clock

@Component
class MetricsBoard(private val contributors: List<MetricsContributor>, private val clock: Clock) {
    fun snapshot(actor: Actor): MetricsSnapshot {
        actor.requireRole(RoleCode.ADMIN)
        val metrics = sortedMapOf<String, Long>()
        contributors.forEach { metrics.putAll(it.metrics()) }
        return MetricsSnapshot(metrics, clock.instant())
    }
}
