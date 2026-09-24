package ru.itmo.aiex.persona.web.dto

import ru.itmo.aiex.persona.domain.Persona
import ru.itmo.aiex.persona.domain.PersonaStatus
import ru.itmo.aiex.persona.domain.RelationshipKind
import java.time.Instant
import java.util.UUID

data class PersonaSummaryResponse(
    val id: UUID,
    val name: String,
    val relationshipKind: RelationshipKind,
    val description: String?,
    val status: PersonaStatus,
    val activeProfileId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Persona.toSummaryResponse() = PersonaSummaryResponse(
    id = id,
    name = name,
    relationshipKind = relationshipKind,
    description = description,
    status = status,
    activeProfileId = activeProfileId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
