package ru.itmo.aiex.persona.infrastructure

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import ru.itmo.aiex.persona.domain.PersonaTag
import ru.itmo.aiex.persona.domain.port.PersonaTagRepository
import java.util.UUID

@Repository
internal class PersonaTagRepositoryAdapter(private val jpa: PersonaTagJpaRepository, private val entityManager: EntityManager) :
    PersonaTagRepository {
    override fun findByPersona(personaId: UUID): List<PersonaTag> = jpa.findAllByPersona(personaId)

    override fun insert(personaTag: PersonaTag) = entityManager.persist(personaTag)

    override fun delete(personaTag: PersonaTag) = entityManager.remove(personaTag)

    override fun deleteAllByPersona(personaId: UUID): Int = jpa.deleteAllByPersona(personaId)

    override fun existsByTag(tagId: Long): Boolean = jpa.existsByTag(tagId)
}
