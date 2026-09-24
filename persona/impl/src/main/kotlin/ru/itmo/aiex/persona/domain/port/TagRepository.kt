package ru.itmo.aiex.persona.domain.port

import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.persona.domain.Tag

interface TagRepository {
    fun findById(id: Long): Tag?

    fun findByCodes(codes: Collection<String>): List<Tag>

    fun existsByCode(code: String): Boolean

    fun findPage(page: PageQuery): PageView<Tag>

    fun saveAndFlush(tag: Tag): Tag

    fun deleteAndFlush(tag: Tag)
}
