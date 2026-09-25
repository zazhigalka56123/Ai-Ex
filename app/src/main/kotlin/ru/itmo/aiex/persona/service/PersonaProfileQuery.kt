package ru.itmo.aiex.persona.service

import ru.itmo.aiex.persona.dto.PersonaProfileView
import java.util.UUID

interface PersonaProfileQuery {
    fun findActiveProfile(personaId: UUID): PersonaProfileView?
}
