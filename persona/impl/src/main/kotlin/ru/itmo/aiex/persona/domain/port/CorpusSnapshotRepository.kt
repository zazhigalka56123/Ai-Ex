package ru.itmo.aiex.persona.domain.port

import ru.itmo.aiex.persona.domain.StoredCorpusSnapshot
import java.util.UUID

interface CorpusSnapshotRepository {
    fun insert(snapshot: StoredCorpusSnapshot)

    fun findLatest(personaId: UUID): StoredCorpusSnapshot?
}
