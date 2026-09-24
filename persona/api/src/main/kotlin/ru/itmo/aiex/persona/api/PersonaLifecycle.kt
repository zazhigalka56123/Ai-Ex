package ru.itmo.aiex.persona.api

import ru.itmo.aiex.common.security.Actor
import java.util.UUID

interface PersonaLifecycle {
    fun startTraining(personaId: UUID, ownerId: UUID, importId: UUID)

    fun trainingFailed(personaId: UUID, importId: UUID)

    fun rebuildFrom(personaId: UUID, snapshot: CorpusSnapshot): ProfileRebuildResult

    fun archive(personaId: UUID, actor: Actor)
}
