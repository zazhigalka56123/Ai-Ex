package ru.itmo.aiex.persona.repository

import jakarta.persistence.EntityManager
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.persistence.toPageView
import ru.itmo.aiex.persona.entity.PersonaProfileVersion
import java.util.UUID

@Repository
internal class ProfileVersionRepositoryAdapter(private val jpa: ProfileVersionJpaRepository, private val entityManager: EntityManager) :
    ProfileVersionRepository {
    override fun insert(version: PersonaProfileVersion) = entityManager.persist(version)

    override fun findById(id: UUID): PersonaProfileVersion? = jpa.findByIdOrNull(id)

    override fun findPageByPersona(personaId: UUID, page: PageQuery): PageView<PersonaProfileVersion> =
        jpa.findAllByPersonaId(personaId, page.toStablePageable()).toPageView()

    override fun maxVersionNo(personaId: UUID): Int = jpa.maxVersionNo(personaId)

    override fun deactivateAll(personaId: UUID): Int = jpa.deactivateAll(personaId)

    override fun countActive(personaId: UUID): Long = jpa.countByPersonaIdAndActiveTrue(personaId)
}
