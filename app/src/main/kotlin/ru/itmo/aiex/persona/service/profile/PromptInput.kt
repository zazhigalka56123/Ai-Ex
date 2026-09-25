package ru.itmo.aiex.persona.service.profile

import ru.itmo.aiex.persona.dto.StyleView
import ru.itmo.aiex.persona.entity.RelationshipKind
data class PromptInput(
    val personaName: String,
    val relationshipKind: RelationshipKind,
    val summary: String,
    val traits: List<DerivedTrait>,
    val tagTitles: List<String>,
    val style: StyleView,
)
