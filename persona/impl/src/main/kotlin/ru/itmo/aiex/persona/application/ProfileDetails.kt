package ru.itmo.aiex.persona.application

import ru.itmo.aiex.persona.api.CorpusStats
import ru.itmo.aiex.persona.api.StyleView
import java.time.Instant
import java.util.UUID

data class ProfileDetails(
    val personaId: UUID,
    val profileId: UUID,
    val versionNo: Int,
    val systemPrompt: String,
    val style: StyleView,
    val corpusStats: CorpusStats,
    val traits: List<TraitItem>,
    val createdAt: Instant,
)
