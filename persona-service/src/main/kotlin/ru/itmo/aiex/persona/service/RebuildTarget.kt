package ru.itmo.aiex.persona.service

import ru.itmo.aiex.persona.dto.CorpusSnapshot
import java.util.UUID

data class RebuildTarget(val personaId: UUID, val snapshotId: UUID, val personaName: String, val snapshot: CorpusSnapshot)
