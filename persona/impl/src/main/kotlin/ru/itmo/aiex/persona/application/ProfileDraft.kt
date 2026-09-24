package ru.itmo.aiex.persona.application

import ru.itmo.aiex.persona.api.StyleView
import ru.itmo.aiex.persona.domain.profile.DerivedTag
import ru.itmo.aiex.persona.domain.profile.DerivedTrait

data class ProfileDraft(
    val traits: List<DerivedTrait>,
    val autoTags: List<DerivedTag>,
    val style: StyleView,
    val summary: String,
    val styleJson: String,
    val statsJson: String,
)
