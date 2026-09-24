package ru.itmo.aiex.care.domain.port

import ru.itmo.aiex.care.domain.Specialist
import ru.itmo.aiex.care.domain.SpecialistStatus
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import java.util.UUID

interface SpecialistRepository {
    fun saveAndFlush(specialist: Specialist): Specialist

    fun findById(id: UUID): Specialist?

    fun findByIdForUpdate(id: UUID): Specialist?

    fun existsByUserId(userId: UUID): Boolean

    fun findActivePage(specializationCode: String?, page: PageQuery): PageView<Specialist>

    fun incrementBookedCount(id: UUID)

    fun decrementBookedCount(id: UUID)

    fun countByStatus(status: SpecialistStatus): Long
}
