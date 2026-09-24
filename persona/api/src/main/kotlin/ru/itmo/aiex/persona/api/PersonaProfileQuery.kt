package ru.itmo.aiex.persona.api

import java.util.UUID

interface PersonaProfileQuery {
    fun findActiveProfile(personaId: UUID): PersonaProfileView?
}
