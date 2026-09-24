package ru.itmo.aiex.care.domain.port

import ru.itmo.aiex.care.domain.Specialization
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView

interface SpecializationRepository {
    fun findPage(page: PageQuery): PageView<Specialization>

    fun findById(id: Long): Specialization?

    fun findByCodes(codes: Collection<String>): List<Specialization>

    fun existsByCode(code: String): Boolean

    fun saveAndFlush(specialization: Specialization): Specialization

    fun deleteAndFlush(specialization: Specialization)

    fun isInUse(id: Long): Boolean
}
