package ru.itmo.aiex.persona.infrastructure

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import ru.itmo.aiex.persona.domain.Persona
import ru.itmo.aiex.persona.domain.PersonaStatus
import java.util.UUID

internal interface PersonaJpaRepository : JpaRepository<Persona, UUID> {
    fun findAllByOwnerIdAndStatus(ownerId: UUID, status: PersonaStatus, pageable: Pageable): Page<Persona>

    fun findAllByOwnerIdAndStatusNot(ownerId: UUID, status: PersonaStatus, pageable: Pageable): Page<Persona>

    fun countByStatus(status: PersonaStatus): Long
}
