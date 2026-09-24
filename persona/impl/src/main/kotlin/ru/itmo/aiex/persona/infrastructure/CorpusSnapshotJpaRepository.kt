package ru.itmo.aiex.persona.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import ru.itmo.aiex.persona.domain.StoredCorpusSnapshot
import java.util.UUID

internal interface CorpusSnapshotJpaRepository : JpaRepository<StoredCorpusSnapshot, UUID> {
    fun findFirstByPersonaIdOrderByCreatedAtDescIdDesc(personaId: UUID): StoredCorpusSnapshot?
}
