package ru.itmo.aiex.admin.service

import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import ru.itmo.aiex.care.service.SpecializationCatalog
import ru.itmo.aiex.common.dictionary.DictionaryCatalog
import ru.itmo.aiex.common.dictionary.DictionaryEntry
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.persona.service.TagCatalog
@Component
class DictionaryAdministration(@param:Lazy private val tags: TagCatalog, private val specializations: SpecializationCatalog) {
    fun getEntries(kind: DictionaryKind, page: PageQuery): PageView<DictionaryEntry> = catalogOf(kind).getEntries(page)

    fun getEntry(kind: DictionaryKind, id: Long): DictionaryEntry = catalogOf(kind).getEntry(id)

    fun createEntry(actor: Actor, kind: DictionaryKind, code: String, title: String): DictionaryEntry {
        actor.requireRole(RoleCode.ADMIN)
        return catalogOf(kind).createEntry(code, title)
    }

    fun updateEntry(actor: Actor, kind: DictionaryKind, id: Long, title: String): DictionaryEntry {
        actor.requireRole(RoleCode.ADMIN)
        return catalogOf(kind).updateEntry(id, title)
    }

    fun deleteEntry(actor: Actor, kind: DictionaryKind, id: Long) {
        actor.requireRole(RoleCode.ADMIN)
        catalogOf(kind).deleteEntry(id)
    }

    private fun catalogOf(kind: DictionaryKind): DictionaryCatalog = when (kind) {
        DictionaryKind.TAGS -> tags
        DictionaryKind.SPECIALIZATIONS -> specializations
    }
}
