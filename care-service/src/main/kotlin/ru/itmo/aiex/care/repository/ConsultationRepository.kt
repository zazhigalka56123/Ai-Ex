package ru.itmo.aiex.care.repository

import ru.itmo.aiex.care.entity.ConsultationSession
import ru.itmo.aiex.care.entity.SessionStatus
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import java.util.UUID

interface ConsultationRepository {
    fun saveAndFlush(session: ConsultationSession): ConsultationSession

    fun findById(id: UUID): ConsultationSession?

    fun findPageForParticipant(userId: UUID, status: SessionStatus?, page: PageQuery): PageView<ConsultationSession>

    fun existsActiveOnSlot(slotId: UUID): Boolean

    fun existsActiveSharing(conversationId: UUID, specialistUserId: UUID): Boolean

    fun existsActive(userId: UUID, specialistId: UUID): Boolean

    fun countByStatus(status: SessionStatus): Long
}
