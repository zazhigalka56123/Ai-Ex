package ru.itmo.aiex.persona.service

import ru.itmo.aiex.persona.dto.StyleView
import ru.itmo.aiex.persona.service.profile.DerivedTag
import ru.itmo.aiex.persona.service.profile.DerivedTrait
data class ProfileDraft(
    val traits: List<DerivedTrait>,
    val autoTags: List<DerivedTag>,
    val style: StyleView,
    val summary: String,
    val styleJson: String,
    val statsJson: String,
)
