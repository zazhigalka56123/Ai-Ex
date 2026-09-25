package ru.itmo.aiex.persona.repository

import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.persona.entity.Persona
import ru.itmo.aiex.persona.entity.PersonaStatus
import java.util.UUID

interface PersonaRepository {
    fun save(persona: Persona): Persona

    fun saveAndFlush(persona: Persona): Persona

    fun findById(id: UUID): Persona?

    fun findPageByOwner(ownerId: UUID, status: PersonaStatus?, page: PageQuery): PageView<Persona>

    fun countByStatus(status: PersonaStatus): Long

    fun flush()
}
