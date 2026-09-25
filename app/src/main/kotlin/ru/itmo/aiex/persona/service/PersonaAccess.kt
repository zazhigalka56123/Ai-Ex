package ru.itmo.aiex.persona.service

import ru.itmo.aiex.persona.dto.PersonaSummaryView

import java.util.UUID

interface PersonaAccess {
    fun assertOwned(personaId: UUID, userId: UUID): PersonaSummaryView

    fun findSummary(personaId: UUID): PersonaSummaryView?
}
