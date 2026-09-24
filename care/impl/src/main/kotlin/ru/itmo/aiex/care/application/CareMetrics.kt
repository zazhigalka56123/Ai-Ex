package ru.itmo.aiex.care.application

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.care.domain.SessionStatus
import ru.itmo.aiex.care.domain.SpecialistStatus
import ru.itmo.aiex.care.domain.port.ConsultationRepository
import ru.itmo.aiex.care.domain.port.SpecialistRepository
import ru.itmo.aiex.common.metrics.MetricsContributor

@Component
@Transactional(readOnly = true)
class CareMetrics(private val specialists: SpecialistRepository, private val sessions: ConsultationRepository) : MetricsContributor {
    override fun metrics(): Map<String, Long> = buildMap {
        put("specialists.active", specialists.countByStatus(SpecialistStatus.ACTIVE))
        SessionStatus.entries.forEach { put("consultations.${it.name.lowercase()}", sessions.countByStatus(it)) }
    }
}
