package ru.itmo.aiex.persona.service

import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.persona.dto.CorpusSnapshot
import ru.itmo.aiex.persona.dto.ProfileRebuildResult
import java.util.UUID

interface PersonaLifecycle : PersonaArchiver {
    fun startTraining(personaId: UUID, ownerId: UUID, importId: UUID)

    fun trainingFailed(personaId: UUID, importId: UUID)

    fun rebuildFrom(personaId: UUID, snapshot: CorpusSnapshot): ProfileRebuildResult

    override fun archive(personaId: UUID, actor: Actor)
}
