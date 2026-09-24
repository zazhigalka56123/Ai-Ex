package ru.itmo.aiex.ingest.infrastructure

import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import ru.itmo.aiex.ingest.domain.ImportedMessage
import java.util.UUID

internal interface ImportedMessageJpaRepository : JpaRepository<ImportedMessage, UUID> {
    @Query("select m from ImportedMessage m where m.chatImport.id = :importId and m.ordinal > :afterOrdinal order by m.ordinal asc")
    fun findAfter(importId: UUID, afterOrdinal: Int, limit: Limit): List<ImportedMessage>

    @Modifying(flushAutomatically = true)
    @Query("delete from ImportedMessage m where m.chatImport.id in (select i.id from ChatImport i where i.personaId = :personaId)")
    fun deleteByPersona(personaId: UUID): Int
}
