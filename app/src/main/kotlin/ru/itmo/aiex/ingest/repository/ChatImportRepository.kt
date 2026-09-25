package ru.itmo.aiex.ingest.repository

import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.ingest.entity.ChatImport
import ru.itmo.aiex.ingest.entity.ImportStatus
import java.util.UUID

interface ChatImportRepository {
    fun save(chatImport: ChatImport): ChatImport

    fun saveAndFlush(chatImport: ChatImport): ChatImport

    fun findById(id: UUID): ChatImport?

    fun findPageByPersona(personaId: UUID, page: PageQuery): PageView<ChatImport>

    fun findIdsByStatus(status: ImportStatus): List<UUID>

    fun count(): Long

    fun countByStatus(status: ImportStatus): Long
}
