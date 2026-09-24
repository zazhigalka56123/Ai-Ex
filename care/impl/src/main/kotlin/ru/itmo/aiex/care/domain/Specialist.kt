package ru.itmo.aiex.care.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.hibernate.annotations.BatchSize
import ru.itmo.aiex.common.security.Actor
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "specialists", schema = "care")
class Specialist(
    @Id
    val id: UUID,
    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    val userId: UUID,
    headline: String,
    bio: String,
    pricePerHour: BigDecimal,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) {
    @field:NotBlank
    @field:Size(max = HEADLINE_MAX_LENGTH)
    @Column(nullable = false, length = HEADLINE_MAX_LENGTH)
    var headline: String = headline
        protected set

    @field:NotBlank
    @field:Size(max = BIO_MAX_LENGTH)
    @Column(nullable = false)
    var bio: String = bio
        protected set

    @field:DecimalMin("0.00")
    @field:Digits(integer = 8, fraction = 2)
    @Column(name = "price_per_hour", nullable = false, precision = 10, scale = 2)
    var pricePerHour: BigDecimal = pricePerHour
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    var status: SpecialistStatus = SpecialistStatus.ACTIVE
        protected set

    @field:Min(0)
    @Column(name = "booked_count", nullable = false, updatable = false)
    var bookedCount: Int = 0
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    @field:NotEmpty
    @ManyToMany(fetch = FetchType.EAGER)
    @BatchSize(size = 50)
    @JoinTable(
        name = "specialist_specializations",
        schema = "care",
        joinColumns = [JoinColumn(name = "specialist_id")],
        inverseJoinColumns = [JoinColumn(name = "specialization_id")],
    )
    var specializations: MutableSet<Specialization> = mutableSetOf()
        protected set

    @Version
    var version: Long? = null
        protected set

    val isActive: Boolean get() = status == SpecialistStatus.ACTIVE

    fun isOwnedBy(userId: UUID): Boolean = this.userId == userId

    fun isVisibleTo(actor: Actor?): Boolean = isActive || (actor != null && canBeManagedBy(actor))

    fun canBeManagedBy(actor: Actor): Boolean = actor.isAdmin || isOwnedBy(actor.userId)

    fun describe(headline: String?, bio: String?, pricePerHour: BigDecimal?, now: Instant) {
        headline?.let { this.headline = it }
        bio?.let { this.bio = it }
        pricePerHour?.let { this.pricePerHour = it }
        updatedAt = now
    }

    fun changeStatus(newStatus: SpecialistStatus, now: Instant) {
        if (status == newStatus) return
        status = newStatus
        updatedAt = now
    }

    fun replaceSpecializations(items: Collection<Specialization>, now: Instant) {
        require(items.isNotEmpty()) { "У специалиста должна быть хотя бы одна специализация" }
        specializations.clear()
        specializations.addAll(items)
        updatedAt = now
    }

    override fun equals(other: Any?): Boolean = this === other || (other is Specialist && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    companion object {
        const val HEADLINE_MAX_LENGTH = 128
        const val BIO_MAX_LENGTH = 4000
    }
}
