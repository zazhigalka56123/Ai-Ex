package ru.itmo.aiex.care.repository

import ru.itmo.aiex.care.entity.SpecialistSlot
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import java.time.Instant
import java.util.UUID

interface SlotRepository {
    fun add(slot: SpecialistSlot): SpecialistSlot

    fun findById(id: UUID): SpecialistSlot?

    fun findByIdForUpdate(id: UUID): SpecialistSlot?

    fun findStartingBetween(specialistId: UUID, fromExclusive: Instant, toExclusive: Instant): List<SpecialistSlot>

    fun findFreePage(specialistId: UUID, fromExclusive: Instant, toExclusive: Instant, page: PageQuery): PageView<SpecialistSlot>
}
