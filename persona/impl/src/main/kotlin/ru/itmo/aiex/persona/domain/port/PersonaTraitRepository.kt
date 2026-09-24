package ru.itmo.aiex.persona.domain.port

import ru.itmo.aiex.persona.domain.PersonaTrait
import java.util.UUID

interface PersonaTraitRepository {
    fun insert(trait: PersonaTrait)

    fun findByPersona(personaId: UUID): List<PersonaTrait>

    fun deleteAuto(personaId: UUID): Int
}
