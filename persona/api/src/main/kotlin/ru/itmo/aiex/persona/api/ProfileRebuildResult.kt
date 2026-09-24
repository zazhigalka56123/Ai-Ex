package ru.itmo.aiex.persona.api

import java.util.UUID

data class ProfileRebuildResult(val personaId: UUID, val profileId: UUID, val versionNo: Int, val status: PersonaState)
