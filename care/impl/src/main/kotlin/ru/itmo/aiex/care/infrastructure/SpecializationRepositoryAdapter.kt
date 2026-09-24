package ru.itmo.aiex.care.infrastructure

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import ru.itmo.aiex.care.domain.Specialization
import ru.itmo.aiex.care.domain.port.SpecializationRepository
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.persistence.toPageView

@Repository
internal class SpecializationRepositoryAdapter(private val jpa: SpecializationJpaRepository, private val specialists: SpecialistJpaRepository) :
    SpecializationRepository {
    override fun findPage(page: PageQuery): PageView<Specialization> = jpa.findAll(PageRequest.of(page.page, page.size, Sort.by("code"))).toPageView()

    override fun findById(id: Long): Specialization? = jpa.findByIdOrNull(id)

    override fun findByCodes(codes: Collection<String>): List<Specialization> = if (codes.isEmpty()) emptyList() else jpa.findAllByCodeIn(codes)

    override fun existsByCode(code: String): Boolean = jpa.existsByCode(code)

    override fun saveAndFlush(specialization: Specialization): Specialization = jpa.saveAndFlush(specialization)

    override fun deleteAndFlush(specialization: Specialization) {
        jpa.delete(specialization)
        jpa.flush()
    }

    override fun isInUse(id: Long): Boolean = specialists.existsBySpecializationsId(id)
}
