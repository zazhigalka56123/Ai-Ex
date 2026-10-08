package ru.itmo.aiex.persona.dto

import io.swagger.v3.oas.annotations.media.Schema
import ru.itmo.aiex.persona.entity.PersonaStatus
import ru.itmo.aiex.persona.entity.RelationshipKind
import ru.itmo.aiex.persona.service.PersonaDetails
import java.time.Instant
import java.util.UUID

data class PersonaResponse(
    val id: UUID,
    val name: String,
    val relationshipKind: RelationshipKind,
    val description: String?,
    val status: PersonaStatus,
    val traits: List<TraitResponse>,
    val tags: List<PersonaTagResponse>,
    @field:Schema(description = "Активная версия профиля; `null`, пока профиль не собран или персона архивна")
    val activeProfile: ActiveProfileResponse?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant?,
)

fun PersonaDetails.toResponse() = PersonaResponse(
    id = id,
    name = name,
    relationshipKind = relationshipKind,
    description = description,
    status = status,
    traits = traits.map { it.toResponse() },
    tags = tags.map { it.toResponse() },
    activeProfile = activeProfile?.toResponse(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    archivedAt = archivedAt,
)
