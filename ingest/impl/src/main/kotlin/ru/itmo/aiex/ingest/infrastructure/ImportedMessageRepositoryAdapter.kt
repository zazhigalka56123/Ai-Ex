package ru.itmo.aiex.ingest.infrastructure

import jakarta.persistence.EntityManager
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Repository
import ru.itmo.aiex.common.id.Ids
import ru.itmo.aiex.ingest.domain.ChatImport
import ru.itmo.aiex.ingest.domain.ImportedMessage
import ru.itmo.aiex.ingest.domain.MessageRow
import ru.itmo.aiex.ingest.domain.port.ImportedMessageRepository
import java.util.UUID

@Repository
internal class ImportedMessageRepositoryAdapter(private val jpa: ImportedMessageJpaRepository, private val entityManager: EntityManager) :
    ImportedMessageRepository {
    override fun insertAll(importId: UUID, rows: List<MessageRow>, batchSize: Int) {
        require(batchSize > 0) { "batchSize должен быть > 0" }
        rows.chunked(batchSize).forEach { chunk ->
            val chatImport = entityManager.getReference(ChatImport::class.java, importId)
            chunk.forEach { row -> entityManager.persist(ImportedMessage(Ids.next(), chatImport, row.author, row.body, row.sentAt, row.ordinal)) }
            entityManager.flush()
            entityManager.clear()
        }
    }

    override fun findAfter(importId: UUID, afterOrdinal: Int, limit: Int): List<ImportedMessage> =
        jpa.findAfter(importId, afterOrdinal, Limit.of(limit))

    override fun deleteByPersona(personaId: UUID): Int = jpa.deleteByPersona(personaId)

    override fun count(): Long = jpa.count()
}
