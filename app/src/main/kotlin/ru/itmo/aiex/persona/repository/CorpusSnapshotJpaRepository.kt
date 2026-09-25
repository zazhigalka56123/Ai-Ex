package ru.itmo.aiex.persona.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.itmo.aiex.persona.entity.StoredCorpusSnapshot
import java.util.UUID

internal interface CorpusSnapshotJpaRepository : JpaRepository<StoredCorpusSnapshot, UUID> {
    fun findFirstByPersonaIdOrderByCreatedAtDescIdDesc(personaId: UUID): StoredCorpusSnapshot?
}
