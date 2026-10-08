package ru.itmo.aiex.persona.entity

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "persona_tags", schema = "persona")
class PersonaTag(persona: Persona, tag: Tag, weight: BigDecimal, source: TraitSource, createdAt: Instant) {
    @EmbeddedId
    val id: PersonaTagId = PersonaTagId(persona.id, requireNotNull(tag.id) { "Тег должен быть сохранён" })

    @MapsId("personaId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "persona_id")
    val persona: Persona = persona

    @MapsId("tagId")
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tag_id")
    val tag: Tag = tag

    @field:DecimalMin("0.0")
    @field:DecimalMax("1.0")
    @Column(nullable = false, precision = 4, scale = 3)
    var weight: BigDecimal = weight
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    var source: TraitSource = source
        protected set

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = createdAt

    fun assignManually(newWeight: BigDecimal) {
        weight = newWeight
        source = TraitSource.MANUAL
    }

    fun reweighAuto(newWeight: BigDecimal) {
        if (source == TraitSource.AUTO) weight = newWeight
    }

    override fun equals(other: Any?): Boolean = this === other || (other is PersonaTag && other.id == id)

    override fun hashCode(): Int = id.hashCode()
}
