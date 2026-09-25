package ru.itmo.aiex.care.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import ru.itmo.aiex.care.service.ConsultationChange
import ru.itmo.aiex.care.service.ConsultationRules
import ru.itmo.aiex.common.security.Actor
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "consultation_sessions", schema = "care")
class ConsultationSession(
    @Id
    val id: UUID,
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialist_id", nullable = false, updatable = false)
    val specialist: Specialist,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false, updatable = false)
    val slot: SpecialistSlot,
    @Column(name = "shared_conversation_id", updatable = false)
    val sharedConversationId: UUID?,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) {
    @Column(name = "starts_at", nullable = false, updatable = false)
    val startsAt: Instant = slot.startsAt

    @field:Min(15)
    @field:Max(240)
    @Column(name = "duration_min", nullable = false, updatable = false)
    val durationMin: Int = slot.durationMin

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    var status: SessionStatus = SessionStatus.REQUESTED
        protected set

    @field:Min(1)
    @field:Max(5)
    @Column
    var rating: Short? = null
        protected set

    @field:Size(max = NOTES_MAX_LENGTH)
    @Column
    var summary: String? = null
        protected set

    @field:Size(max = NOTES_MAX_LENGTH)
    @Column
    var recommendations: String? = null
        protected set

    @field:Size(max = CANCEL_REASON_MAX_LENGTH)
    @Column(name = "cancel_reason", length = CANCEL_REASON_MAX_LENGTH)
    var cancelReason: String? = null
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    @Version
    var version: Long? = null
        protected set

    fun roleOf(actor: Actor): ConsultationRole? = when {
        specialist.isOwnedBy(actor.userId) -> ConsultationRole.SPECIALIST
        userId == actor.userId -> ConsultationRole.CLIENT
        actor.isAdmin -> ConsultationRole.ADMIN
        else -> null
    }

    fun applyChange(role: ConsultationRole, change: ConsultationChange, now: Instant): Boolean {
        val target = ConsultationRules.check(role, status, change)
        change.summary?.let { summary = it }
        change.recommendations?.let { recommendations = it }
        change.rating?.let { rating = it }
        if (target == SessionStatus.CANCELLED) change.cancelReason?.let { cancelReason = it }
        val statusChanged = target != status
        status = target
        updatedAt = now
        return statusChanged
    }

    override fun equals(other: Any?): Boolean = this === other || (other is ConsultationSession && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    companion object {
        const val NOTES_MAX_LENGTH = 10_000
        const val CANCEL_REASON_MAX_LENGTH = 500
    }
}
