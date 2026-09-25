package ru.itmo.aiex.persona.dto

import java.util.UUID

data class ProfileRebuildResponse(val personaId: UUID, val profileId: UUID, val versionNo: Int, val status: PersonaState)

fun ProfileRebuildResult.toResponse() = ProfileRebuildResponse(personaId, profileId, versionNo, status)
