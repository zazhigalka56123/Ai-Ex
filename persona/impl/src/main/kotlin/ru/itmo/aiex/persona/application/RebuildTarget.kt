package ru.itmo.aiex.persona.application

import ru.itmo.aiex.persona.api.CorpusSnapshot
import java.util.UUID

data class RebuildTarget(val personaId: UUID, val snapshotId: UUID, val personaName: String, val snapshot: CorpusSnapshot)
