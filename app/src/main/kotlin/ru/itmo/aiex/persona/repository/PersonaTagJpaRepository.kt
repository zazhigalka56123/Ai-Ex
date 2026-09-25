package ru.itmo.aiex.persona.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import ru.itmo.aiex.persona.entity.PersonaTag
import ru.itmo.aiex.persona.entity.PersonaTagId
import java.util.UUID

internal interface PersonaTagJpaRepository : JpaRepository<PersonaTag, PersonaTagId> {
    @Query("select pt from PersonaTag pt join fetch pt.tag where pt.persona.id = :personaId")
    fun findAllByPersona(personaId: UUID): List<PersonaTag>

    @Modifying(flushAutomatically = true)
    @Query("delete from PersonaTag pt where pt.persona.id = :personaId")
    fun deleteAllByPersona(personaId: UUID): Int

    @Query("select count(pt) > 0 from PersonaTag pt where pt.tag.id = :tagId")
    fun existsByTag(tagId: Long): Boolean
}
