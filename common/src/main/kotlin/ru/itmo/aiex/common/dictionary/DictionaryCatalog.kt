package ru.itmo.aiex.common.dictionary

import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView

interface DictionaryCatalog {
    fun list(page: PageQuery): PageView<DictionaryEntry>

    fun get(id: Long): DictionaryEntry

    fun create(code: String, title: String): DictionaryEntry

    fun update(id: Long, title: String): DictionaryEntry

    fun delete(id: Long)
}
