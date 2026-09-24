package ru.itmo.aiex.care.infrastructure

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.itmo.aiex.care.domain.ConsultationSession
import ru.itmo.aiex.care.domain.SessionStatus
import java.util.UUID

internal interface ConsultationJpaRepository : JpaRepository<ConsultationSession, UUID> {
    @EntityGraph(attributePaths = ["specialist", "slot"])
    @Query("select cs from ConsultationSession cs where cs.id = :id")
    fun findDetailedById(@Param("id") id: UUID): ConsultationSession?

    @EntityGraph(attributePaths = ["specialist", "slot"])
    @Query(
        value = """
            select cs from ConsultationSession cs
            where (cs.userId = :userId or cs.specialist.userId = :userId) and cs.status in :statuses
        """,
        countQuery = """
            select count(cs) from ConsultationSession cs
            where (cs.userId = :userId or cs.specialist.userId = :userId) and cs.status in :statuses
        """,
    )
    fun findForParticipant(
        @Param("userId") userId: UUID,
        @Param("statuses") statuses: Collection<SessionStatus>,
        pageable: Pageable,
    ): Page<ConsultationSession>

    fun existsBySlotIdAndStatusIn(slotId: UUID, statuses: Collection<SessionStatus>): Boolean

    fun existsBySharedConversationIdAndSpecialistUserIdAndStatusIn(
        sharedConversationId: UUID,
        specialistUserId: UUID,
        statuses: Collection<SessionStatus>,
    ): Boolean

    fun existsByUserIdAndSpecialistIdAndStatusIn(userId: UUID, specialistId: UUID, statuses: Collection<SessionStatus>): Boolean

    fun countByStatus(status: SessionStatus): Long
}
