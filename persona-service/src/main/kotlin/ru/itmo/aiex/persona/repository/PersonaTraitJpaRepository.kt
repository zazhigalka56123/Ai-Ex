package ru.itmo.aiex.persona.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import ru.itmo.aiex.persona.entity.PersonaTrait
import ru.itmo.aiex.persona.entity.TraitSource
import java.util.UUID

internal interface PersonaTraitJpaRepository : JpaRepository<PersonaTrait, UUID> {
    fun findAllByPersonaIdOrderByTraitKeyAsc(personaId: UUID): List<PersonaTrait>

    @Modifying(flushAutomatically = true)
    @Query("delete from PersonaTrait t where t.persona.id = :personaId and t.source = :source")
    fun deleteBySource(personaId: UUID, source: TraitSource): Int
}
