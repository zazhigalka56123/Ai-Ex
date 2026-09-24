package ru.itmo.aiex.dialog.infrastructure

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.dialog.domain.Conversation
import ru.itmo.aiex.dialog.domain.ConversationStatus
import ru.itmo.aiex.dialog.domain.port.ConversationRepository
import ru.itmo.aiex.persistence.toPageView
import ru.itmo.aiex.persistence.toPageable
import java.time.Instant
import java.util.UUID

@Repository
internal class ConversationRepositoryAdapter(private val jpa: ConversationJpaRepository) : ConversationRepository {
    override fun save(conversation: Conversation): Conversation = jpa.saveAndFlush(conversation)

    override fun findById(id: UUID): Conversation? = jpa.findByIdOrNull(id)

    override fun existsByIdAndUserId(id: UUID, userId: UUID): Boolean = jpa.existsByIdAndUserId(id, userId)

    override fun findPage(userId: UUID, status: ConversationStatus?, personaId: UUID?, page: PageQuery): PageView<Conversation> {
        val requested = page.toPageable()
        val pageable = PageRequest.of(requested.pageNumber, requested.pageSize, requested.sort.and(Sort.by(Sort.Direction.DESC, "id")))
        val result =
            when {
                status != null && personaId != null -> jpa.findAllByUserIdAndStatusAndPersonaId(userId, status, personaId, pageable)
                status != null -> jpa.findAllByUserIdAndStatus(userId, status, pageable)
                personaId != null -> jpa.findAllByUserIdAndPersonaId(userId, personaId, pageable)
                else -> jpa.findAllByUserId(userId, pageable)
            }
        return result.toPageView()
    }

    override fun bumpCounters(id: UUID, delta: Int, at: Instant): Int = jpa.bumpCounters(id, delta, at)

    override fun archiveAllByPersona(personaId: UUID): Int =
        jpa.archiveAllByPersona(personaId, ConversationStatus.ACTIVE, ConversationStatus.ARCHIVED)

    override fun countByStatus(status: ConversationStatus): Long = jpa.countByStatus(status)
}
