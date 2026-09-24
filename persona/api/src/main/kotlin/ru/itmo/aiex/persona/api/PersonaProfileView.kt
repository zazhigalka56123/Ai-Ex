package ru.itmo.aiex.persona.api

import java.util.UUID

data class PersonaProfileView(
    val personaId: UUID,
    val profileId: UUID,
    val versionNo: Int,
    val personaName: String,
    val systemPrompt: String,
    val style: StyleView,
    val traits: List<TraitView>,
    val tags: List<String>,
)
