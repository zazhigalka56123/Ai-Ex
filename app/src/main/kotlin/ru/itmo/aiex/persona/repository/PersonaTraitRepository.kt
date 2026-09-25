package ru.itmo.aiex.persona.repository

import ru.itmo.aiex.persona.entity.PersonaTrait
import java.util.UUID

interface PersonaTraitRepository {
    fun insert(trait: PersonaTrait)

    fun findByPersona(personaId: UUID): List<PersonaTrait>

    fun deleteAuto(personaId: UUID): Int
}
