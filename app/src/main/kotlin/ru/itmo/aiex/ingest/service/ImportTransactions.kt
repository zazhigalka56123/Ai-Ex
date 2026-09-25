package ru.itmo.aiex.ingest.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.id.Ids
import ru.itmo.aiex.common.time.nowMicros
import ru.itmo.aiex.ingest.entity.ChatImport
import ru.itmo.aiex.ingest.entity.ImportErrorCode
import ru.itmo.aiex.ingest.entity.ImportSource
import ru.itmo.aiex.ingest.entity.ImportStatus

import ru.itmo.aiex.ingest.repository.ChatImportRepository
import ru.itmo.aiex.ingest.repository.ImportedMessageRepository
import java.time.Clock
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ImportTransactions(private val imports: ChatImportRepository, private val messages: ImportedMessageRepository, private val clock: Clock) {
    @Transactional
    fun createPending(personaId: UUID, ownerId: UUID, source: ImportSource, filename: String, sizeBytes: Long): ChatImport = imports.save(
        ChatImport(Ids.next(), personaId, ownerId, source, filename, sizeBytes, clock.nowMicros()),
    )

    @Transactional
    fun markParsing(importId: UUID) {
        val chatImport = find(importId)
        chatImport.startParsing()
        imports.saveAndFlush(chatImport)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun commitParsed(importId: UUID, rows: List<MessageRow>, result: ParseResult): ChatImport {
        messages.insertAll(importId, rows, BATCH_SIZE)

        val chatImport = find(importId)
        chatImport.markParsed(result, clock.nowMicros())
        return imports.saveAndFlush(chatImport)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markFailed(importId: UUID, code: ImportErrorCode, message: String): ChatImport {
        val chatImport = find(importId)
        chatImport.markFailed(code, message, clock.nowMicros())
        return imports.saveAndFlush(chatImport)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markRebuildFailed(importId: UUID, message: String): ChatImport {
        val chatImport = find(importId)
        chatImport.markRebuildFailed(message)
        return imports.saveAndFlush(chatImport)
    }

    fun find(importId: UUID): ChatImport = imports.findById(importId) ?: throw NotFoundException.of(ErrorCode.IMPORT_NOT_FOUND, importId)

    fun findStuckIds(): List<UUID> = imports.findIdsByStatus(ImportStatus.PARSING)

    private companion object {
        const val BATCH_SIZE = 500
    }
}
