package ru.itmo.aiex.persona.application

import org.springframework.stereotype.Component
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.persona.api.CorpusSnapshot
import ru.itmo.aiex.persona.api.PersonaLifecycle
import ru.itmo.aiex.persona.api.ProfileRebuildResult
import java.util.UUID

@Component
class PersonaLifecycleAdapter(
    private val transactions: ProfileTransactions,
    private val rebuilds: ProfileRebuildService,
    private val personaService: PersonaService,
) : PersonaLifecycle {
    override fun startTraining(personaId: UUID, ownerId: UUID, importId: UUID) = transactions.startTraining(personaId, ownerId, importId)

    override fun trainingFailed(personaId: UUID, importId: UUID) = transactions.trainingFailed(personaId, importId)

    override fun rebuildFrom(personaId: UUID, snapshot: CorpusSnapshot): ProfileRebuildResult = rebuilds.rebuildFrom(personaId, snapshot)

    override fun archive(personaId: UUID, actor: Actor) = personaService.archive(personaId, actor)
}
