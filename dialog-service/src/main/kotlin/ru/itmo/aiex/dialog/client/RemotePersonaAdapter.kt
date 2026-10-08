package ru.itmo.aiex.dialog.client

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import ru.itmo.aiex.persona.dto.PersonaProfileView
import ru.itmo.aiex.persona.dto.PersonaSummaryView
import ru.itmo.aiex.persona.service.PersonaAccess
import ru.itmo.aiex.persona.service.PersonaProfileQuery
import ru.itmo.aiex.remote.PersonaClient
import java.util.UUID

@Component
@Profile("microservice")
class RemotePersonaAdapter(private val personas: PersonaClient) :
    PersonaAccess,
    PersonaProfileQuery {
    override fun assertOwned(personaId: UUID, userId: UUID): PersonaSummaryView = personas.assertOwned(personaId, userId)

    override fun findSummary(personaId: UUID): PersonaSummaryView? = personas.findSummary(personaId)

    override fun findActiveProfile(personaId: UUID): PersonaProfileView? = personas.findActiveProfile(personaId)
}
