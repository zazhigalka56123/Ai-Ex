package ru.itmo.aiex.persona.service

import ru.itmo.aiex.persona.dto.CorpusStats
import ru.itmo.aiex.persona.dto.StyleView
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
