package ru.itmo.aiex.care.repository

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import ru.itmo.aiex.care.entity.Specialist
import ru.itmo.aiex.care.entity.SpecialistStatus

import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.persistence.toPageView
import java.util.UUID

@Repository
internal class SpecialistRepositoryAdapter(private val jpa: SpecialistJpaRepository) : SpecialistRepository {
    override fun saveAndFlush(specialist: Specialist): Specialist = jpa.saveAndFlush(specialist)

    override fun findById(id: UUID): Specialist? = jpa.findByIdOrNull(id)

    override fun findByIdForUpdate(id: UUID): Specialist? = jpa.findLockedById(id)

    override fun existsByUserId(userId: UUID): Boolean = jpa.existsByUserId(userId)

    override fun findActivePage(specializationCode: String?, page: PageQuery): PageView<Specialist> {
        val pageable = page.toStablePageable()
        val result =
            if (specializationCode == null) {
                jpa.findAllByStatus(SpecialistStatus.ACTIVE, pageable)
            } else {
                jpa.findAllByStatusAndSpecialization(SpecialistStatus.ACTIVE, specializationCode, pageable)
            }
        return result.toPageView()
    }

    override fun incrementBookedCount(id: UUID) {
        jpa.incrementBookedCount(id)
    }

    override fun decrementBookedCount(id: UUID) {
        jpa.decrementBookedCount(id)
    }

    override fun countByStatus(status: SpecialistStatus): Long = jpa.countByStatus(status)
}
