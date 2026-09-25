package ru.itmo.aiex.persona.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.persona.entity.PersonaProfileVersion
import ru.itmo.aiex.persona.repository.PersonaTraitRepository
import ru.itmo.aiex.persona.repository.ProfileVersionRepository
import java.util.UUID

@Service
@Transactional(readOnly = true)
class PersonaProfileService(
    private val personaService: PersonaService,
    private val versions: ProfileVersionRepository,
    private val traits: PersonaTraitRepository,
    private val json: PersonaJson,
) {
    fun activeProfile(actor: Actor, personaId: UUID): ProfileDetails {
        val persona = personaService.findOwned(actor, personaId)
        val version =
            persona.activeProfileId?.let(versions::findById)?.takeIf { it.active }
                ?: throw ConflictException(ErrorCode.PERSONA_NOT_READY, "У персоны нет активного профиля (статус ${persona.status})")
        return ProfileDetails(
            personaId = persona.id,
            profileId = version.id,
            versionNo = version.versionNo,
            systemPrompt = version.systemPrompt,
            style = json.readStyle(version.style),
            corpusStats = json.readStats(version.corpusStats),
            traits = traits.findByPersona(persona.id).map { it.toItem() },
            createdAt = version.createdAt,
        )
    }

    fun versions(actor: Actor, personaId: UUID, page: PageQuery): PageView<PersonaProfileVersion> {
        personaService.findOwned(actor, personaId)
        return versions.findPageByPersona(personaId, page)
    }
}
