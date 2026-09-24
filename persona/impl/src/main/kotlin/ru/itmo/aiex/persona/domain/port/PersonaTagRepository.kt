package ru.itmo.aiex.persona.domain.port

import ru.itmo.aiex.persona.domain.PersonaTag
import java.util.UUID

interface PersonaTagRepository {
    fun findByPersona(personaId: UUID): List<PersonaTag>

    fun insert(personaTag: PersonaTag)

    fun delete(personaTag: PersonaTag)

    fun deleteAllByPersona(personaId: UUID): Int

    fun existsByTag(tagId: Long): Boolean
}
