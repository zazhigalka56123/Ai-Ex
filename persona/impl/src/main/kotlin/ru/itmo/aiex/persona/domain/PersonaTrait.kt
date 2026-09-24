package ru.itmo.aiex.persona.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "persona_traits", schema = "persona")
class PersonaTrait(
    @Id
    val id: UUID,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "persona_id", nullable = false, updatable = false)
    val persona: Persona,
    @field:NotBlank
    @field:Size(max = 64)
    @Column(name = "trait_key", nullable = false, length = 64)
    val traitKey: String,
    @field:NotBlank
    @field:Size(max = 256)
    @Column(name = "trait_value", nullable = false, length = 256)
    val traitValue: String,
    @field:DecimalMin("0.0")
    @field:DecimalMax("1.0")
    @Column(nullable = false, precision = 4, scale = 3)
    val weight: BigDecimal,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    val source: TraitSource,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is PersonaTrait && other.id == id)

    override fun hashCode(): Int = id.hashCode()
}
