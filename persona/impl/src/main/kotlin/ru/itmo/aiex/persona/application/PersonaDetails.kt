package ru.itmo.aiex.persona.application

import ru.itmo.aiex.persona.domain.PersonaStatus
import ru.itmo.aiex.persona.domain.RelationshipKind
import java.time.Instant
import java.util.UUID

data class PersonaDetails(
    val id: UUID,
    val name: String,
    val relationshipKind: RelationshipKind,
    val description: String?,
    val status: PersonaStatus,
    val traits: List<TraitItem>,
    val tags: List<TagItem>,
    val activeProfile: ActiveProfileRef?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant?,
)
