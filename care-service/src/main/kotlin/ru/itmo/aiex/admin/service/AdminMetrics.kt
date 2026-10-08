package ru.itmo.aiex.admin.service

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.admin.entity.FlagStatus
import ru.itmo.aiex.admin.repository.ModerationFlagRepository
import ru.itmo.aiex.common.metrics.MetricsContributor
@Component
@Transactional(readOnly = true)
class AdminMetrics(private val flags: ModerationFlagRepository) : MetricsContributor {
    override fun metrics(): Map<String, Long> = FlagStatus.entries.associate { "flags.${it.name.lowercase()}" to flags.countByStatus(it) }
}
