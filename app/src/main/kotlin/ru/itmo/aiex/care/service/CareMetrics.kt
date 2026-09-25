package ru.itmo.aiex.care.service

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.care.entity.SessionStatus
import ru.itmo.aiex.care.entity.SpecialistStatus
import ru.itmo.aiex.care.repository.ConsultationRepository
import ru.itmo.aiex.care.repository.SpecialistRepository
import ru.itmo.aiex.common.metrics.MetricsContributor
@Component
@Transactional(readOnly = true)
class CareMetrics(private val specialists: SpecialistRepository, private val sessions: ConsultationRepository) : MetricsContributor {
    override fun metrics(): Map<String, Long> = buildMap {
        put("specialists.active", specialists.countByStatus(SpecialistStatus.ACTIVE))
        SessionStatus.entries.forEach { put("consultations.${it.name.lowercase()}", sessions.countByStatus(it)) }
    }
}
