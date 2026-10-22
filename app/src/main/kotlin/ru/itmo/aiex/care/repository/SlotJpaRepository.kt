package ru.itmo.aiex.care.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import ru.itmo.aiex.care.entity.SessionStatus
import ru.itmo.aiex.care.entity.SpecialistSlot
import java.time.Instant
import java.util.UUID

internal interface SlotJpaRepository : JpaRepository<SpecialistSlot, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sl from SpecialistSlot sl where sl.id = :id")
    fun findLockedById(id: UUID): SpecialistSlot?

    @Query("select sl from SpecialistSlot sl where sl.specialist.id = :specialistId and sl.startsAt > :from and sl.startsAt < :to")
    fun findStartingBetween(specialistId: UUID, from: Instant, to: Instant): List<SpecialistSlot>

    @Query(
        value = """
            select sl from SpecialistSlot sl
            where sl.specialist.id = :specialistId and sl.startsAt > :from and sl.startsAt < :to
              and not exists (select 1 from ConsultationSession cs where cs.slot = sl and cs.status in :active)
        """,
        countQuery = """
            select count(sl) from SpecialistSlot sl
            where sl.specialist.id = :specialistId and sl.startsAt > :from and sl.startsAt < :to
              and not exists (select 1 from ConsultationSession cs where cs.slot = sl and cs.status in :active)
        """,
    )
    fun findFree(specialistId: UUID, from: Instant, to: Instant, active: Collection<SessionStatus>, pageable: Pageable): Page<SpecialistSlot>
}
