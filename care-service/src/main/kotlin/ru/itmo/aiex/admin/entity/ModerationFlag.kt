package ru.itmo.aiex.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.Size
import ru.itmo.aiex.admin.service.FlaggedMessage
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.IllegalStateTransitionException
import ru.itmo.aiex.common.moderation.FlagReason
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "moderation_flags", schema = "admin")
class ModerationFlag private constructor(
    @Id
    val id: UUID,
    @Column(name = "message_id", nullable = false, updatable = false)
    val messageId: UUID,
    @Column(name = "conversation_id", nullable = false, updatable = false)
    val conversationId: UUID,
    @Column(name = "persona_id", nullable = false, updatable = false)
    val personaId: UUID,
    @Column(name = "reporter_id", updatable = false)
    val reporterId: UUID?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24, updatable = false)
    val source: FlagSource,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24, updatable = false)
    val reason: FlagReason,
    @field:Size(max = COMMENT_MAX_LENGTH)
    @Column(length = COMMENT_MAX_LENGTH, updatable = false)
    val comment: String?,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    var status: FlagStatus = FlagStatus.OPEN
        protected set

    @field:Size(max = RESOLUTION_MAX_LENGTH)
    @Column
    var resolution: String? = null
        protected set

    @Column(name = "assignee_id")
    var assigneeId: UUID? = null
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null
        protected set

    @Version
    var version: Long? = null
        protected set

    fun review(target: FlagStatus, resolution: String?, reviewerId: UUID, now: Instant) {
        if (!status.canTransitionTo(target)) {
            throw IllegalStateTransitionException(ErrorCode.FLAG_INVALID_STATE, status.name, target.name)
        }
        status = target
        resolution?.let { this.resolution = it }
        assigneeId = reviewerId
        updatedAt = now
        if (target.isTerminal) resolvedAt = now
    }

    override fun equals(other: Any?): Boolean = this === other || (other is ModerationFlag && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    companion object {
        const val COMMENT_MAX_LENGTH = 500
        const val RESOLUTION_MAX_LENGTH = 4000

        fun reportedBy(id: UUID, message: FlaggedMessage, reporterId: UUID, reason: FlagReason, comment: String?, now: Instant) =
            ModerationFlag(id, message.messageId, message.conversationId, message.personaId, reporterId, FlagSource.USER, reason, comment, now)

        fun raisedByGuardrail(id: UUID, message: FlaggedMessage, reason: FlagReason, details: String, now: Instant) = ModerationFlag(
            id,
            message.messageId,
            message.conversationId,
            message.personaId,
            null,
            FlagSource.GUARDRAIL,
            reason,
            details.take(COMMENT_MAX_LENGTH).ifBlank { null },
            now,
        )
    }
}
