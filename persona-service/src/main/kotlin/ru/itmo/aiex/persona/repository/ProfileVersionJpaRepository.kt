package ru.itmo.aiex.persona.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import ru.itmo.aiex.persona.entity.PersonaProfileVersion
import java.util.UUID

internal interface ProfileVersionJpaRepository : JpaRepository<PersonaProfileVersion, UUID> {
    fun findAllByPersonaId(personaId: UUID, pageable: Pageable): Page<PersonaProfileVersion>

    @Query("select coalesce(max(v.versionNo), 0) from PersonaProfileVersion v where v.persona.id = :personaId")
    fun maxVersionNo(personaId: UUID): Int

    @Modifying(flushAutomatically = true)
    @Query("update PersonaProfileVersion v set v.active = false where v.persona.id = :personaId and v.active = true")
    fun deactivateAll(personaId: UUID): Int

    fun countByPersonaIdAndActiveTrue(personaId: UUID): Long
}
