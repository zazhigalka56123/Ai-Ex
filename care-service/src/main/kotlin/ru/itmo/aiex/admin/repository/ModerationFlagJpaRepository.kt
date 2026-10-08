package ru.itmo.aiex.admin.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import ru.itmo.aiex.admin.entity.FlagSource
import ru.itmo.aiex.admin.entity.FlagStatus
import ru.itmo.aiex.admin.entity.ModerationFlag
import ru.itmo.aiex.common.moderation.FlagReason
import java.util.UUID

internal interface ModerationFlagJpaRepository : JpaRepository<ModerationFlag, UUID> {
    fun existsByMessageIdAndReporterId(messageId: UUID, reporterId: UUID): Boolean

    fun existsByMessageIdAndReasonAndSource(messageId: UUID, reason: FlagReason, source: FlagSource): Boolean

    fun findAllByStatusInAndReasonIn(statuses: Collection<FlagStatus>, reasons: Collection<FlagReason>, pageable: Pageable): Page<ModerationFlag>

    fun countByStatus(status: FlagStatus): Long
}
