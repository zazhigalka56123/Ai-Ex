package ru.itmo.aiex.care.repository

import org.springframework.stereotype.Repository
import ru.itmo.aiex.care.entity.ConsultationSession
import ru.itmo.aiex.care.entity.SessionStatus

import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.persistence.toPageView
import java.util.UUID

@Repository
internal class ConsultationRepositoryAdapter(private val jpa: ConsultationJpaRepository) : ConsultationRepository {
    override fun saveAndFlush(session: ConsultationSession): ConsultationSession = jpa.saveAndFlush(session)

    override fun findById(id: UUID): ConsultationSession? = jpa.findDetailedById(id)

    override fun findPageForParticipant(userId: UUID, status: SessionStatus?, page: PageQuery): PageView<ConsultationSession> {
        val statuses = status?.let(::setOf) ?: SessionStatus.entries.toSet()
        return jpa.findForParticipant(userId, statuses, page.toStablePageable()).toPageView()
    }

    override fun existsActiveOnSlot(slotId: UUID): Boolean = jpa.existsBySlotIdAndStatusIn(slotId, SessionStatus.ACTIVE)

    override fun existsActiveSharing(conversationId: UUID, specialistUserId: UUID): Boolean =
        jpa.existsBySharedConversationIdAndSpecialistUserIdAndStatusIn(conversationId, specialistUserId, SessionStatus.ACTIVE)

    override fun existsActive(userId: UUID, specialistId: UUID): Boolean =
        jpa.existsByUserIdAndSpecialistIdAndStatusIn(userId, specialistId, SessionStatus.ACTIVE)

    override fun countByStatus(status: SessionStatus): Long = jpa.countByStatus(status)
}
