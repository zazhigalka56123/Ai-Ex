package ru.itmo.aiex.ingest.repository

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.ingest.entity.ChatImport
import ru.itmo.aiex.ingest.entity.ImportStatus

import ru.itmo.aiex.common.persistence.toPageView
import ru.itmo.aiex.common.persistence.toPageable
import java.util.UUID

@Repository
internal class ChatImportRepositoryAdapter(private val jpa: ChatImportJpaRepository) : ChatImportRepository {
    override fun save(chatImport: ChatImport): ChatImport = jpa.save(chatImport)

    override fun saveAndFlush(chatImport: ChatImport): ChatImport = jpa.saveAndFlush(chatImport)

    override fun findById(id: UUID): ChatImport? = jpa.findByIdOrNull(id)

    override fun findPageByPersona(personaId: UUID, page: PageQuery): PageView<ChatImport> {
        val pageable = page.toPageable()
        return jpa.findAllByPersonaId(
            personaId,
            PageRequest.of(pageable.pageNumber, pageable.pageSize, pageable.sort.and(Sort.by("id"))),
        ).toPageView()
    }

    override fun findIdsByStatus(status: ImportStatus): List<UUID> = jpa.findIdsByStatus(status)

    override fun count(): Long = jpa.count()

    override fun countByStatus(status: ImportStatus): Long = jpa.countByStatus(status)
}
