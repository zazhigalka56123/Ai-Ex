package ru.itmo.aiex.care.service

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

import ru.itmo.aiex.care.repository.ConsultationRepository
import java.util.UUID

@Component
@Transactional(readOnly = true)
class ConsultationQueryAdapter(private val sessions: ConsultationRepository) : ConsultationQuery {
    override fun isConversationSharedWith(conversationId: UUID, specialistUserId: UUID): Boolean =
        sessions.existsActiveSharing(conversationId, specialistUserId)

    override fun hasActiveSession(userId: UUID, specialistId: UUID): Boolean = sessions.existsActive(userId, specialistId)
}
