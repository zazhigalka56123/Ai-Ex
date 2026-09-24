package ru.itmo.aiex.care.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "specialist_slots", schema = "care")
class SpecialistSlot(
    @Id
    val id: UUID,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specialist_id", nullable = false, updatable = false)
    val specialist: Specialist,
    @Column(name = "starts_at", nullable = false, updatable = false)
    val startsAt: Instant,
    @field:Min(15)
    @field:Max(240)
    @Column(name = "duration_min", nullable = false, updatable = false)
    val durationMin: Int,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) {
    val endsAt: Instant get() = SlotSchedule.end(startsAt, durationMin)

    fun overlaps(otherStart: Instant, otherDurationMin: Int): Boolean = SlotSchedule.overlaps(startsAt, durationMin, otherStart, otherDurationMin)

    override fun equals(other: Any?): Boolean = this === other || (other is SpecialistSlot && other.id == id)

    override fun hashCode(): Int = id.hashCode()
}
