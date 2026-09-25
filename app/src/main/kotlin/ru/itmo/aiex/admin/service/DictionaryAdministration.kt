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
    fun list(kind: DictionaryKind, page: PageQuery): PageView<DictionaryEntry> = catalog(kind).list(page)

    fun get(kind: DictionaryKind, id: Long): DictionaryEntry = catalog(kind).get(id)

    fun create(actor: Actor, kind: DictionaryKind, code: String, title: String): DictionaryEntry {
        actor.requireRole(RoleCode.ADMIN)
        return catalog(kind).create(code, title)
    }

    fun update(actor: Actor, kind: DictionaryKind, id: Long, title: String): DictionaryEntry {
        actor.requireRole(RoleCode.ADMIN)
        return catalog(kind).update(id, title)
    }

    fun delete(actor: Actor, kind: DictionaryKind, id: Long) {
        actor.requireRole(RoleCode.ADMIN)
        catalog(kind).delete(id)
    }

    private fun catalog(kind: DictionaryKind): DictionaryCatalog = when (kind) {
        DictionaryKind.TAGS -> tags
        DictionaryKind.SPECIALIZATIONS -> specializations
    }
}
