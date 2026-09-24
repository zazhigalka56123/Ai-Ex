package ru.itmo.aiex.dialog.infrastructure

import jakarta.persistence.EntityManager
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import ru.itmo.aiex.common.paging.TimeIdPosition
import ru.itmo.aiex.dialog.domain.Message
import ru.itmo.aiex.dialog.domain.MessageScope
import ru.itmo.aiex.dialog.domain.port.MessageRepository
import java.util.UUID

@Repository
internal class MessageRepositoryAdapter(private val jpa: MessageJpaRepository, private val entityManager: EntityManager) : MessageRepository {
    override fun insert(message: Message): Message {
        entityManager.persist(message)
        return message
    }

    override fun findById(id: UUID): Message? = jpa.findByIdOrNull(id)

    override fun findWithConversation(id: UUID): Message? = jpa.findWithConversation(id)

    override fun findSlice(conversationId: UUID, before: TimeIdPosition?, scope: MessageScope, limit: Int): List<Message> {
        if (limit <= 0) return emptyList()
        return when (scope) {
            MessageScope.ALL ->
                if (before == null) {
                    jpa.findNewest(conversationId, limit)
                } else {
                    jpa.findOlder(conversationId, before.timestamp, before.id, limit)
                }

            MessageScope.FLAGGED_ONLY ->
                if (before == null) {
                    jpa.findNewestFlagged(conversationId, limit)
                } else {
                    jpa.findOlderFlagged(conversationId, before.timestamp, before.id, limit)
                }
        }
    }

    override fun markFlagged(id: UUID): Int = jpa.markFlagged(id)

    override fun count(): Long = jpa.count()

    override fun countFlagged(): Long = jpa.countByFlaggedTrue()
}
