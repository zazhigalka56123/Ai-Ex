package ru.itmo.aiex.common.dictionary

import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
interface DictionaryCatalog {
    fun getEntries(page: PageQuery): PageView<DictionaryEntry>

    fun getEntry(id: Long): DictionaryEntry

    fun createEntry(code: String, title: String): DictionaryEntry

    fun updateEntry(id: Long, title: String): DictionaryEntry

    fun deleteEntry(id: Long)
}
