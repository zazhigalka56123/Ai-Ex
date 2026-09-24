package ru.itmo.aiex.persona.api

import java.util.UUID

interface PersonaAccess {
    fun assertOwned(personaId: UUID, userId: UUID): PersonaSummaryView

    fun findSummary(personaId: UUID): PersonaSummaryView?
}
