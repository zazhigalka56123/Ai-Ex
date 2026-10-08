package ru.itmo.aiex.dialog.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.itmo.aiex.dialog.entity.Message
import java.time.Instant
import java.util.UUID

internal interface MessageJpaRepository : JpaRepository<Message, UUID> {
    @Query("select m from Message m join fetch m.conversation where m.id = :id")
    fun findWithConversation(@Param("id") id: UUID): Message?

    @Query(
        value = """
            SELECT * FROM dialog.messages
            WHERE conversation_id = :conversationId
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findNewest(@Param("conversationId") conversationId: UUID, @Param("limit") limit: Int): List<Message>

    @Query(
        value = """
            SELECT * FROM dialog.messages
            WHERE conversation_id = :conversationId
              AND (created_at, id) < (CAST(:createdAt AS timestamptz), CAST(:id AS uuid))
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findOlder(
        @Param("conversationId") conversationId: UUID,
        @Param("createdAt") createdAt: Instant,
        @Param("id") id: UUID,
        @Param("limit") limit: Int,
    ): List<Message>

    @Query(
        value = """
            SELECT * FROM dialog.messages
            WHERE conversation_id = :conversationId AND flagged
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findNewestFlagged(@Param("conversationId") conversationId: UUID, @Param("limit") limit: Int): List<Message>

    @Query(
        value = """
            SELECT * FROM dialog.messages
            WHERE conversation_id = :conversationId AND flagged
              AND (created_at, id) < (CAST(:createdAt AS timestamptz), CAST(:id AS uuid))
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findOlderFlagged(
        @Param("conversationId") conversationId: UUID,
        @Param("createdAt") createdAt: Instant,
        @Param("id") id: UUID,
        @Param("limit") limit: Int,
    ): List<Message>

    @Modifying
    @Query("update Message m set m.flagged = true where m.id = :id and m.flagged = false")
    fun markFlagged(@Param("id") id: UUID): Int

    fun countByFlaggedTrue(): Long
}
