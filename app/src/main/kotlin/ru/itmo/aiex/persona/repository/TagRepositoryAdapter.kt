package ru.itmo.aiex.persona.repository

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.persistence.toPageView
import ru.itmo.aiex.persona.entity.Tag

@Repository
internal class TagRepositoryAdapter(private val jpa: TagJpaRepository) : TagRepository {
    override fun findById(id: Long): Tag? = jpa.findByIdOrNull(id)

    override fun findByCodes(codes: Collection<String>): List<Tag> = if (codes.isEmpty()) emptyList() else jpa.findAllByCodeIn(codes)

    override fun existsByCode(code: String): Boolean = jpa.existsByCode(code)

    override fun findPage(page: PageQuery): PageView<Tag> = jpa.findAll(page.toStablePageable()).toPageView()

    override fun saveAndFlush(tag: Tag): Tag = jpa.saveAndFlush(tag)

    override fun deleteAndFlush(tag: Tag) {
        jpa.delete(tag)
        jpa.flush()
    }
}
