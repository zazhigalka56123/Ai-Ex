package ru.itmo.aiex.persona.repository

import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.persona.entity.PersonaProfileVersion
import java.util.UUID

interface ProfileVersionRepository {
    fun insert(version: PersonaProfileVersion)

    fun findById(id: UUID): PersonaProfileVersion?

    fun findPageByPersona(personaId: UUID, page: PageQuery): PageView<PersonaProfileVersion>

    fun maxVersionNo(personaId: UUID): Int

    fun deactivateAll(personaId: UUID): Int

    fun countActive(personaId: UUID): Long
}
