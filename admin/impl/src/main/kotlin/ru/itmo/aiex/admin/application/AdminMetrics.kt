package ru.itmo.aiex.admin.application

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.admin.domain.FlagStatus
import ru.itmo.aiex.admin.domain.port.ModerationFlagRepository
import ru.itmo.aiex.common.metrics.MetricsContributor

@Component
@Transactional(readOnly = true)
class AdminMetrics(private val flags: ModerationFlagRepository) : MetricsContributor {
    override fun metrics(): Map<String, Long> = FlagStatus.entries.associate { "flags.${it.name.lowercase()}" to flags.countByStatus(it) }
}
