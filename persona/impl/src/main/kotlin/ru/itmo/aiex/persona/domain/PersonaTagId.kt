package ru.itmo.aiex.persona.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

@Embeddable
class PersonaTagId(
    @Column(name = "persona_id", nullable = false)
    val personaId: UUID,
    @Column(name = "tag_id", nullable = false)
    val tagId: Long,
) : Serializable {
    override fun equals(other: Any?): Boolean = this === other || (other is PersonaTagId && other.personaId == personaId && other.tagId == tagId)

    override fun hashCode(): Int = 31 * personaId.hashCode() + tagId.hashCode()

    companion object {
        private const val serialVersionUID = 1L
    }
}
