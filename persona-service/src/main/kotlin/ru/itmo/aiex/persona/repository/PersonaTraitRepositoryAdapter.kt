package ru.itmo.aiex.persona.repository

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import ru.itmo.aiex.persona.entity.PersonaTrait
import ru.itmo.aiex.persona.entity.TraitSource
import java.util.UUID

@Repository
internal class PersonaTraitRepositoryAdapter(private val jpa: PersonaTraitJpaRepository, private val entityManager: EntityManager) :
    PersonaTraitRepository {
    override fun insert(trait: PersonaTrait) = entityManager.persist(trait)

    override fun findByPersona(personaId: UUID): List<PersonaTrait> = jpa.findAllByPersonaIdOrderByTraitKeyAsc(personaId)

    override fun deleteAuto(personaId: UUID): Int = jpa.deleteBySource(personaId, TraitSource.AUTO)
}
