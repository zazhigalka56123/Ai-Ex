package ru.itmo.aiex.persona.repository

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import ru.itmo.aiex.persona.entity.StoredCorpusSnapshot

import java.util.UUID

@Repository
internal class CorpusSnapshotRepositoryAdapter(private val jpa: CorpusSnapshotJpaRepository, private val entityManager: EntityManager) :
    CorpusSnapshotRepository {
    override fun insert(snapshot: StoredCorpusSnapshot) = entityManager.persist(snapshot)

    override fun findLatest(personaId: UUID): StoredCorpusSnapshot? = jpa.findFirstByPersonaIdOrderByCreatedAtDescIdDesc(personaId)
}
