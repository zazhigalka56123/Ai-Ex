package ru.itmo.aiex.dialog.repository

import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.dialog.entity.Conversation
import ru.itmo.aiex.dialog.entity.ConversationStatus
import java.time.Instant
import java.util.UUID

interface ConversationRepository {
    fun save(conversation: Conversation): Conversation

    fun findById(id: UUID): Conversation?

    fun existsByIdAndUserId(id: UUID, userId: UUID): Boolean

    fun findPage(userId: UUID, status: ConversationStatus?, personaId: UUID?, page: PageQuery): PageView<Conversation>

    fun bumpCounters(id: UUID, delta: Int, at: Instant): Int

    fun archiveAllByPersona(personaId: UUID): Int

    fun countByStatus(status: ConversationStatus): Long
}
