package ru.itmo.aiex.persona.repository

import ru.itmo.aiex.persona.entity.StoredCorpusSnapshot
import java.util.UUID

interface CorpusSnapshotRepository {
    fun insert(snapshot: StoredCorpusSnapshot)

    fun findLatest(personaId: UUID): StoredCorpusSnapshot?
}
