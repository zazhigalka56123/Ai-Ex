package ru.itmo.aiex.persona.service

import ru.itmo.aiex.common.security.Actor
import java.util.UUID

fun interface PersonaArchiver {
    fun archive(personaId: UUID, actor: Actor)
}
