package ru.itmo.aiex.dialog.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import ru.itmo.aiex.dialog.entity.Conversation
import ru.itmo.aiex.dialog.entity.ConversationStatus
import java.time.Instant
import java.util.UUID

internal interface ConversationJpaRepository : JpaRepository<Conversation, UUID> {
    fun findAllByUserId(userId: UUID, pageable: Pageable): Page<Conversation>

    fun findAllByUserIdAndStatus(userId: UUID, status: ConversationStatus, pageable: Pageable): Page<Conversation>

    fun findAllByUserIdAndPersonaId(userId: UUID, personaId: UUID, pageable: Pageable): Page<Conversation>

    fun findAllByUserIdAndStatusAndPersonaId(userId: UUID, status: ConversationStatus, personaId: UUID, pageable: Pageable): Page<Conversation>

    fun existsByIdAndUserId(id: UUID, userId: UUID): Boolean

    fun countByStatus(status: ConversationStatus): Long

    @Modifying
    @Query(
        value = """
            UPDATE dialog.conversations
            SET message_count = message_count + :delta, last_message_at = GREATEST(last_message_at, :at)
            WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun bumpCounters(id: UUID, delta: Int, at: Instant): Int

    @Modifying
    @Query(
        """
            update Conversation c set c.status = :archived, c.version = c.version + 1
            where c.personaId = :personaId and c.status = :active
        """,
    )
    fun archiveAllByPersona(personaId: UUID, active: ConversationStatus, archived: ConversationStatus): Int
}
