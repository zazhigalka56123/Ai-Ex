package ru.itmo.aiex.persona.web.dto

import ru.itmo.aiex.persona.api.PersonaState
import ru.itmo.aiex.persona.api.ProfileRebuildResult
import java.util.UUID

data class ProfileRebuildResponse(val personaId: UUID, val profileId: UUID, val versionNo: Int, val status: PersonaState)

fun ProfileRebuildResult.toResponse() = ProfileRebuildResponse(personaId, profileId, versionNo, status)
