package ru.itmo.aiex.care.repository

import jakarta.persistence.EntityManager
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import ru.itmo.aiex.care.entity.SessionStatus
import ru.itmo.aiex.care.entity.SpecialistSlot
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.persistence.toPageView
import java.time.Instant
import java.util.UUID

@Repository
internal class SlotRepositoryAdapter(private val jpa: SlotJpaRepository, private val entityManager: EntityManager) : SlotRepository {
    override fun add(slot: SpecialistSlot): SpecialistSlot {
        entityManager.persist(slot)
        entityManager.flush()
        return slot
    }

    override fun findById(id: UUID): SpecialistSlot? = jpa.findByIdOrNull(id)

    override fun findByIdForUpdate(id: UUID): SpecialistSlot? = jpa.findLockedById(id)

    override fun findStartingBetween(specialistId: UUID, fromExclusive: Instant, toExclusive: Instant): List<SpecialistSlot> =
        jpa.findStartingBetween(specialistId, fromExclusive, toExclusive)

    override fun findFreePage(specialistId: UUID, fromExclusive: Instant, toExclusive: Instant, page: PageQuery): PageView<SpecialistSlot> =
        jpa.findFree(specialistId, fromExclusive, toExclusive, SessionStatus.ACTIVE, page.toStablePageable()).toPageView()
}
