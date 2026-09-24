package ru.itmo.aiex.persona.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.error.ValidationException
import ru.itmo.aiex.common.events.DomainEventPublisher
import ru.itmo.aiex.common.events.PersonaArchived
import ru.itmo.aiex.common.id.Ids
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.common.time.nowMicros
import ru.itmo.aiex.persona.domain.Persona
import ru.itmo.aiex.persona.domain.PersonaStatus
import ru.itmo.aiex.persona.domain.PersonaTag
import ru.itmo.aiex.persona.domain.Tag
import ru.itmo.aiex.persona.domain.TraitSource
import ru.itmo.aiex.persona.domain.port.PersonaRepository
import ru.itmo.aiex.persona.domain.port.PersonaTagRepository
import ru.itmo.aiex.persona.domain.port.ProfileVersionRepository
import ru.itmo.aiex.persona.domain.port.TagRepository
import ru.itmo.aiex.persona.domain.profile.FULL_WEIGHT
import java.time.Clock
import java.util.UUID

@Service
@Transactional(readOnly = true)
class PersonaService(
    private val personas: PersonaRepository,
    private val tags: TagRepository,
    private val personaTags: PersonaTagRepository,
    private val versions: ProfileVersionRepository,
    private val events: DomainEventPublisher,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun create(actor: Actor, command: CreatePersonaCommand): PersonaDetails {
        actor.requireRole(RoleCode.USER)
        val codes = command.tagCodes.map(::normalizeCode).toSet()
        val resolved = resolveTags(codes, field = "tagCodes")
        val now = clock.nowMicros()
        val persona =
            Persona(
                id = Ids.next(),
                ownerId = actor.userId,
                name = command.name.trim(),
                relationshipKind = command.relationshipKind,
                description = command.description?.trim()?.ifEmpty { null },
                createdAt = now,
            )
        resolved.values.forEach { tag -> persona.tags.add(PersonaTag(persona, tag, FULL_WEIGHT, TraitSource.MANUAL, now)) }
        personas.save(persona)
        log.info("Персона {} создана владельцем {}, тегов {}", persona.id, actor.userId, resolved.size)
        return toDetails(persona)
    }

    fun list(actor: Actor, status: PersonaStatus?, page: PageQuery): PageView<Persona> = personas.findPageByOwner(actor.userId, status, page)

    fun get(actor: Actor, personaId: UUID): PersonaDetails = toDetails(findOwned(actor, personaId))

    @Transactional
    fun update(actor: Actor, personaId: UUID, command: UpdatePersonaCommand): PersonaDetails {
        val persona = findOwned(actor, personaId)
        val description = command.description?.trim()
        persona.edit(
            newName = command.name?.trim(),
            newKind = command.relationshipKind,
            newDescription = description?.ifEmpty { null },
            clearDescription = description != null && description.isEmpty(),
            now = clock.nowMicros(),
        )
        return toDetails(personas.saveAndFlush(persona))
    }

    @Transactional
    fun replaceTags(actor: Actor, personaId: UUID, assignments: List<TagAssignment>): PersonaDetails {
        val persona = findOwned(actor, personaId)
        persona.ensureEditable()
        val requested = assignments.associate { normalizeCode(it.code) to (it.weight ?: FULL_WEIGHT) }
        val resolved = resolveTags(requested.keys, field = "tags")
        val now = clock.nowMicros()
        val existing = persona.tags.associateBy { it.tag.code }
        existing.values
            .filter { it.source == TraitSource.MANUAL && it.tag.code !in requested }
            .forEach { persona.tags.remove(it) }
        requested.forEach { (code, weight) ->
            val current = existing[code]
            if (current != null) {
                current.assignManually(weight)
            } else {
                persona.tags.add(PersonaTag(persona, resolved.getValue(code), weight, TraitSource.MANUAL, now))
            }
        }
        persona.touch(now)
        return toDetails(personas.saveAndFlush(persona))
    }

    @Transactional
    fun archive(personaId: UUID, actor: Actor) {
        val persona =
            personas.findById(personaId)?.takeIf { it.isOwnedBy(actor.userId) || actor.isAdmin }
                ?: throw NotFoundException.of(ErrorCode.PERSONA_NOT_FOUND, personaId)
        val now = clock.nowMicros()
        if (!persona.archive(now)) return
        val deactivated = versions.deactivateAll(personaId)
        val untagged = personaTags.deleteAllByPersona(personaId)
        personas.saveAndFlush(persona)
        val byAdmin = !persona.isOwnedBy(actor.userId)
        events.publish(PersonaArchived(personaId, persona.ownerId, actor.userId, byAdmin, now))
        log.info("Персона {} архивирована (администратором: {}), версий снято {}, тегов удалено {}", personaId, byAdmin, deactivated, untagged)
    }

    fun findOwned(actor: Actor, personaId: UUID): Persona =
        personas.findById(personaId)?.takeIf { it.isOwnedBy(actor.userId) } ?: throw NotFoundException.of(ErrorCode.PERSONA_NOT_FOUND, personaId)

    private fun resolveTags(codes: Set<String>, field: String): Map<String, Tag> {
        val found = tags.findByCodes(codes).associateBy { it.code }
        val unknown = codes - found.keys
        if (unknown.isNotEmpty()) {
            throw ValidationException(field, "unknown", "Неизвестные теги: ${unknown.sorted().joinToString()}")
        }
        return found
    }

    private fun normalizeCode(code: String): String = code.trim().lowercase()

    private fun toDetails(persona: Persona): PersonaDetails = PersonaDetails(
        id = persona.id,
        name = persona.name,
        relationshipKind = persona.relationshipKind,
        description = persona.description,
        status = persona.status,
        traits = persona.traits.map { it.toItem() }.sortedBy { it.key },
        tags = persona.tags.map { it.toItem() }.sortedBy { it.code },
        activeProfile = persona.activeProfileId?.let { id -> versions.findById(id)?.let { ActiveProfileRef(it.id, it.versionNo) } },
        createdAt = persona.createdAt,
        updatedAt = persona.updatedAt,
        archivedAt = persona.archivedAt,
    )
}
