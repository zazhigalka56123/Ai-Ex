package ru.itmo.aiex.admin.infrastructure

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import ru.itmo.aiex.admin.domain.FlagSource
import ru.itmo.aiex.admin.domain.FlagStatus
import ru.itmo.aiex.admin.domain.ModerationFlag
import ru.itmo.aiex.admin.domain.port.ModerationFlagRepository
import ru.itmo.aiex.common.moderation.FlagReason
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.persistence.toPageView
import ru.itmo.aiex.persistence.toPageable
import java.util.UUID

@Repository
internal class ModerationFlagRepositoryAdapter(private val jpa: ModerationFlagJpaRepository) : ModerationFlagRepository {
    override fun saveAndFlush(flag: ModerationFlag): ModerationFlag = jpa.saveAndFlush(flag)

    override fun findById(id: UUID): ModerationFlag? = jpa.findByIdOrNull(id)

    override fun existsByMessageAndReporter(messageId: UUID, reporterId: UUID): Boolean = jpa.existsByMessageIdAndReporterId(messageId, reporterId)

    override fun existsGuardrailFlag(messageId: UUID, reason: FlagReason): Boolean =
        jpa.existsByMessageIdAndReasonAndSource(messageId, reason, FlagSource.GUARDRAIL)

    override fun findPage(status: FlagStatus?, reason: FlagReason?, page: PageQuery): PageView<ModerationFlag> {
        val statuses = status?.let(::setOf) ?: FlagStatus.entries.toSet()
        val reasons = reason?.let(::setOf) ?: FlagReason.entries.toSet()

        val pageable = page.toPageable().let { PageRequest.of(it.pageNumber, it.pageSize, it.sort.and(Sort.by("id"))) }
        return jpa.findAllByStatusInAndReasonIn(statuses, reasons, pageable).toPageView()
    }

    override fun countByStatus(status: FlagStatus): Long = jpa.countByStatus(status)
}
