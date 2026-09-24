package ru.itmo.aiex.ingest.domain.port

import ru.itmo.aiex.ingest.domain.ImportedMessage
import ru.itmo.aiex.ingest.domain.MessageRow
import java.util.UUID

interface ImportedMessageRepository {
    fun insertAll(importId: UUID, rows: List<MessageRow>, batchSize: Int)

    fun findAfter(importId: UUID, afterOrdinal: Int, limit: Int): List<ImportedMessage>

    fun deleteByPersona(personaId: UUID): Int

    fun count(): Long
}
