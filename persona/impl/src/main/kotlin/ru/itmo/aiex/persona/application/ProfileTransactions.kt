package ru.itmo.aiex.persona.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.IllegalStateTransitionException
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.events.DomainEventPublisher
import ru.itmo.aiex.common.events.PersonaProfileActivated
import ru.itmo.aiex.common.id.Ids
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.time.nowMicros
import ru.itmo.aiex.persona.api.CorpusSnapshot
import ru.itmo.aiex.persona.api.ProfileRebuildResult
import ru.itmo.aiex.persona.domain.Persona
import ru.itmo.aiex.persona.domain.PersonaProfileVersion
import ru.itmo.aiex.persona.domain.PersonaStatus
import ru.itmo.aiex.persona.domain.PersonaTag
import ru.itmo.aiex.persona.domain.PersonaTrait
import ru.itmo.aiex.persona.domain.StoredCorpusSnapshot
import ru.itmo.aiex.persona.domain.TraitSource
import ru.itmo.aiex.persona.domain.port.CorpusSnapshotRepository
import ru.itmo.aiex.persona.domain.port.PersonaRepository
import ru.itmo.aiex.persona.domain.port.PersonaTagRepository
import ru.itmo.aiex.persona.domain.port.PersonaTraitRepository
import ru.itmo.aiex.persona.domain.port.ProfileVersionRepository
import ru.itmo.aiex.persona.domain.port.TagRepository
import ru.itmo.aiex.persona.domain.profile.DerivedTag
import ru.itmo.aiex.persona.domain.profile.DerivedTrait
import ru.itmo.aiex.persona.domain.profile.PromptBuilder
import ru.itmo.aiex.persona.domain.profile.PromptInput
import ru.itmo.aiex.persona.domain.profile.toWeight
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ProfileTransactions(
    private val personas: PersonaRepository,
    private val traits: PersonaTraitRepository,
    private val versions: ProfileVersionRepository,
    private val snapshots: CorpusSnapshotRepository,
    private val tags: TagRepository,
    private val personaTags: PersonaTagRepository,
    private val json: PersonaJson,
    private val events: DomainEventPublisher,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val promptBuilder = PromptBuilder()

    @Transactional
    fun startTraining(personaId: UUID, ownerId: UUID, importId: UUID) {
        val persona =
            personas.findById(personaId)?.takeIf { it.isOwnedBy(ownerId) } ?: throw NotFoundException.of(ErrorCode.PERSONA_NOT_FOUND, personaId)
        persona.startTraining(clock.nowMicros())
        personas.saveAndFlush(persona)
        log.info("Персона {}: обучение по импорту {} начато", personaId, importId)
    }

    @Transactional
    fun trainingFailed(personaId: UUID, importId: UUID) {
        val persona = personas.findById(personaId) ?: return
        if (persona.trainingFailed(clock.nowMicros())) {
            personas.saveAndFlush(persona)
            log.info("Персона {}: разбор импорта {} упал, статус {}", personaId, importId, persona.status)
        }
    }

    @Transactional
    fun storeSnapshot(personaId: UUID, snapshot: CorpusSnapshot): RebuildTarget {
        val persona = personas.findById(personaId) ?: throw NotFoundException.of(ErrorCode.PERSONA_NOT_FOUND, personaId)
        if (persona.isArchived) {
            throw IllegalStateTransitionException(ErrorCode.PERSONA_INVALID_STATE, PersonaStatus.ARCHIVED.name, PersonaStatus.READY.name)
        }
        val stored = StoredCorpusSnapshot(Ids.next(), personaId, snapshot.importId, json.write(snapshot), clock.nowMicros())
        snapshots.insert(stored)
        return RebuildTarget(personaId, stored.id, persona.name, snapshot)
    }

    fun planManualRebuild(personaId: UUID, actor: Actor): ManualRebuildPlan {
        val persona =
            personas.findById(personaId)?.takeIf { it.isOwnedBy(actor.userId) || actor.isAdmin }
                ?: throw NotFoundException.of(ErrorCode.PERSONA_NOT_FOUND, personaId)
        if (persona.isArchived) throw ConflictException(ErrorCode.PERSONA_INVALID_STATE, "Персона архивирована, пересобрать профиль нельзя")
        val latest =
            snapshots.findLatest(personaId)
                ?: throw ConflictException(ErrorCode.PERSONA_NOT_READY, "Корпус ещё не загружен: сначала импортируйте переписку")
        val active = persona.activeProfileId?.let(versions::findById)
        if (persona.status == PersonaStatus.READY && active != null && active.corpusSnapshotId == latest.id) {
            return ManualRebuildPlan.UpToDate(ProfileRebuildResult(personaId, active.id, active.versionNo, persona.status.toState()))
        }
        return ManualRebuildPlan.Rebuild(RebuildTarget(personaId, latest.id, persona.name, json.readSnapshot(latest.payload)))
    }

    @Transactional
    fun applyProfile(target: RebuildTarget, draft: ProfileDraft): ProfileRebuildResult {
        val persona = personas.findById(target.personaId) ?: throw NotFoundException.of(ErrorCode.PERSONA_NOT_FOUND, target.personaId)
        if (persona.isArchived) {
            throw IllegalStateTransitionException(ErrorCode.PERSONA_INVALID_STATE, PersonaStatus.ARCHIVED.name, PersonaStatus.READY.name)
        }
        val now = clock.nowMicros()

        if (persona.status != PersonaStatus.TRAINING) persona.startTraining(now)

        val allTraits = replaceAutoTraits(persona, draft.traits)
        val tagTitles = upsertAutoTags(persona, draft.autoTags, now)
        val prompt = promptBuilder.build(PromptInput(persona.name, persona.relationshipKind, draft.summary, allTraits, tagTitles, draft.style))

        versions.deactivateAll(persona.id)
        val versionNo = versions.maxVersionNo(persona.id) + 1
        val version = PersonaProfileVersion(Ids.next(), persona, versionNo, prompt, draft.styleJson, draft.statsJson, target.snapshotId, now)
        versions.insert(version)
        persona.activateProfile(version.id, now)
        personas.flush()

        events.publish(PersonaProfileActivated(persona.id, persona.ownerId, version.id, versionNo, now))
        log.info("Персона {}: активирована версия профиля {} ({} черт, {} тегов)", persona.id, versionNo, allTraits.size, tagTitles.size)
        return ProfileRebuildResult(persona.id, version.id, versionNo, persona.status.toState())
    }

    private fun replaceAutoTraits(persona: Persona, derived: List<DerivedTrait>): List<DerivedTrait> {
        traits.deleteAuto(persona.id)
        val manual = traits.findByPersona(persona.id).associateBy { it.traitKey }
        val auto = derived.filterNot { it.key in manual }
        auto.forEach { traits.insert(PersonaTrait(Ids.next(), persona, it.key, it.value, it.weight.toWeight(), TraitSource.AUTO)) }
        return (manual.values.map { DerivedTrait(it.traitKey, it.traitValue, it.weight.toDouble()) } + auto).sortedBy { it.key }
    }

    private fun upsertAutoTags(persona: Persona, derived: List<DerivedTag>, now: Instant): List<String> {
        val dictionary = tags.findByCodes(derived.map { it.code }).associateBy { it.code }
        val matched = derived.filter { it.code in dictionary }.associateBy { it.code }
        val current = personaTags.findByPersona(persona.id)
        current.filter { it.source == TraitSource.AUTO && it.tag.code !in matched }.forEach(personaTags::delete)
        current.forEach { existing -> matched[existing.tag.code]?.let { existing.reweighAuto(it.weight.toWeight()) } }
        val present = current.map { it.tag.code }.toSet()
        matched.values
            .filter { it.code !in present }
            .forEach { personaTags.insert(PersonaTag(persona, dictionary.getValue(it.code), it.weight.toWeight(), TraitSource.AUTO, now)) }
        val kept = current.filter { it.source == TraitSource.MANUAL || it.tag.code in matched }.map { it.tag }
        return (kept + matched.keys.filter { it !in present }.map(dictionary::getValue)).sortedBy { it.code }.map { it.title }
    }
}
