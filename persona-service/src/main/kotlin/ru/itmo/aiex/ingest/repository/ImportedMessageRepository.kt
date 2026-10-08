package ru.itmo.aiex.ingest.repository

import ru.itmo.aiex.ingest.entity.ImportedMessage
import ru.itmo.aiex.ingest.service.MessageRow
import java.util.UUID

interface ImportedMessageRepository {
    fun insertAll(importId: UUID, rows: List<MessageRow>, batchSize: Int)

    fun findAfter(importId: UUID, afterOrdinal: Int, limit: Int): List<ImportedMessage>

    fun deleteByPersona(personaId: UUID): Int

    fun count(): Long
}
