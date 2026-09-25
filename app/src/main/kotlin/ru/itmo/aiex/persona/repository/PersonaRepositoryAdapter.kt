package ru.itmo.aiex.persona.repository

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.persistence.toPageView
import ru.itmo.aiex.persona.entity.Persona
import ru.itmo.aiex.persona.entity.PersonaStatus

import java.util.UUID

@Repository
internal class PersonaRepositoryAdapter(private val jpa: PersonaJpaRepository) : PersonaRepository {
    override fun save(persona: Persona): Persona = jpa.save(persona)

    override fun saveAndFlush(persona: Persona): Persona = jpa.saveAndFlush(persona)

    override fun findById(id: UUID): Persona? = jpa.findByIdOrNull(id)

    override fun findPageByOwner(ownerId: UUID, status: PersonaStatus?, page: PageQuery): PageView<Persona> {
        val pageable = page.toStablePageable()
        val result =
            if (status == null) {
                jpa.findAllByOwnerIdAndStatusNot(ownerId, PersonaStatus.ARCHIVED, pageable)
            } else {
                jpa.findAllByOwnerIdAndStatus(ownerId, status, pageable)
            }
        return result.toPageView()
    }

    override fun countByStatus(status: PersonaStatus): Long = jpa.countByStatus(status)

    override fun flush() = jpa.flush()
}
