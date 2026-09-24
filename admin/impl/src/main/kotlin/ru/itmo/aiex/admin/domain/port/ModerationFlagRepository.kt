package ru.itmo.aiex.admin.domain.port

import ru.itmo.aiex.admin.domain.FlagStatus
import ru.itmo.aiex.admin.domain.ModerationFlag
import ru.itmo.aiex.common.moderation.FlagReason
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import java.util.UUID

interface ModerationFlagRepository {
    fun saveAndFlush(flag: ModerationFlag): ModerationFlag

    fun findById(id: UUID): ModerationFlag?

    fun existsByMessageAndReporter(messageId: UUID, reporterId: UUID): Boolean

    fun existsGuardrailFlag(messageId: UUID, reason: FlagReason): Boolean

    fun findPage(status: FlagStatus?, reason: FlagReason?, page: PageQuery): PageView<ModerationFlag>

    fun countByStatus(status: FlagStatus): Long
}
