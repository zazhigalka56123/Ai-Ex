package ru.itmo.aiex.persona.application

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.persona.api.PersonaProfileQuery
import ru.itmo.aiex.persona.api.PersonaProfileView
import ru.itmo.aiex.persona.api.TraitView
import ru.itmo.aiex.persona.domain.PersonaStatus
import ru.itmo.aiex.persona.domain.port.PersonaRepository
import ru.itmo.aiex.persona.domain.port.PersonaTagRepository
import ru.itmo.aiex.persona.domain.port.PersonaTraitRepository
import ru.itmo.aiex.persona.domain.port.ProfileVersionRepository
import java.util.UUID

@Component
@Transactional(readOnly = true)
class PersonaProfileQueryAdapter(
    private val personas: PersonaRepository,
    private val versions: ProfileVersionRepository,
    private val traits: PersonaTraitRepository,
    private val personaTags: PersonaTagRepository,
    private val json: PersonaJson,
) : PersonaProfileQuery {
    override fun findActiveProfile(personaId: UUID): PersonaProfileView? {
        val persona = personas.findById(personaId)?.takeIf { it.status == PersonaStatus.READY } ?: return null
        val version = persona.activeProfileId?.let(versions::findById)?.takeIf { it.active } ?: return null
        return PersonaProfileView(
            personaId = persona.id,
            profileId = version.id,
            versionNo = version.versionNo,
            personaName = persona.name,
            systemPrompt = version.systemPrompt,
            style = json.readStyle(version.style),
            traits = traits.findByPersona(persona.id).map { TraitView(it.traitKey, it.traitValue, it.weight.toDouble()) },
            tags = personaTags.findByPersona(persona.id).map { it.tag.code }.sorted(),
        )
    }
}
