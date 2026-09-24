package ru.itmo.aiex.persona.application

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.persona.api.PersonaAccess
import ru.itmo.aiex.persona.api.PersonaSummaryView
import ru.itmo.aiex.persona.domain.Persona
import ru.itmo.aiex.persona.domain.port.PersonaRepository
import java.util.UUID

@Component
@Transactional(readOnly = true)
class PersonaAccessAdapter(private val personas: PersonaRepository) : PersonaAccess {
    override fun assertOwned(personaId: UUID, userId: UUID): PersonaSummaryView =
        personas.findById(personaId)?.takeIf { it.isOwnedBy(userId) }?.toSummary()
            ?: throw NotFoundException.of(ErrorCode.PERSONA_NOT_FOUND, personaId)

    override fun findSummary(personaId: UUID): PersonaSummaryView? = personas.findById(personaId)?.toSummary()

    private fun Persona.toSummary() = PersonaSummaryView(id, ownerId, name, status.toState(), activeProfileId)
}
