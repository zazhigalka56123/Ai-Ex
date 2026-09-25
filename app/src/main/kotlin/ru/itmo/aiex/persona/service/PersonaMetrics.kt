package ru.itmo.aiex.persona.service

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.metrics.MetricsContributor
import ru.itmo.aiex.persona.entity.PersonaStatus
import ru.itmo.aiex.persona.repository.PersonaRepository
@Component
@Transactional(readOnly = true)
class PersonaMetrics(private val personas: PersonaRepository) : MetricsContributor {
    override fun metrics(): Map<String, Long> = PersonaStatus.entries.associate { "personas.${it.name.lowercase()}" to personas.countByStatus(it) }
}
