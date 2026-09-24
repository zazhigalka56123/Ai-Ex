package ru.itmo.aiex.ingest.infrastructure

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import ru.itmo.aiex.ingest.domain.ChatImport
import ru.itmo.aiex.ingest.domain.ImportStatus
import java.util.UUID

internal interface ChatImportJpaRepository : JpaRepository<ChatImport, UUID> {
    fun findAllByPersonaId(personaId: UUID, pageable: Pageable): Page<ChatImport>

    @Query("select i.id from ChatImport i where i.status = :status order by i.createdAt asc")
    fun findIdsByStatus(status: ImportStatus): List<UUID>

    fun countByStatus(status: ImportStatus): Long
}
