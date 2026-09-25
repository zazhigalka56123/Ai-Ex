package ru.itmo.aiex.persona.dto

import io.swagger.v3.oas.annotations.media.Schema

import ru.itmo.aiex.persona.service.ProfileDetails
import java.time.Instant
import java.util.UUID

data class ProfileResponse(
    val personaId: UUID,
    val profileId: UUID,
    val versionNo: Int,
    @field:Schema(description = "Системный промпт персоны; строки `- «фраза»` - характерные реплики из переписки")
    val systemPrompt: String,
    val style: StyleView,
    val corpusStats: CorpusStats,
    val traits: List<TraitResponse>,
    val createdAt: Instant,
)

fun ProfileDetails.toResponse() = ProfileResponse(
    personaId = personaId,
    profileId = profileId,
    versionNo = versionNo,
    systemPrompt = systemPrompt,
    style = style,
    corpusStats = corpusStats,
    traits = traits.map { it.toResponse() },
    createdAt = createdAt,
)
