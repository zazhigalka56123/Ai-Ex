package ru.itmo.aiex.persona.domain.profile

import ru.itmo.aiex.persona.api.StyleView
import ru.itmo.aiex.persona.domain.RelationshipKind

data class PromptInput(
    val personaName: String,
    val relationshipKind: RelationshipKind,
    val summary: String,
    val traits: List<DerivedTrait>,
    val tagTitles: List<String>,
    val style: StyleView,
)
