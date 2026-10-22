package ru.itmo.aiex.admin.controller

import org.springframework.http.ResponseEntity
import ru.itmo.aiex.admin.dto.CreateDictionaryEntryRequest
import ru.itmo.aiex.admin.dto.DictionaryEntryResponse
import ru.itmo.aiex.admin.dto.UpdateDictionaryEntryRequest
import ru.itmo.aiex.admin.dto.toResponse
import ru.itmo.aiex.admin.service.DictionaryAdministration
import ru.itmo.aiex.admin.service.DictionaryKind
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.web.Responses
class DictionaryEndpoints(private val kind: DictionaryKind, private val basePath: String, private val dictionaries: DictionaryAdministration) {
    fun getEntries(page: PageQuery): ResponseEntity<List<DictionaryEntryResponse>> =
        Responses.page(dictionaries.getEntries(kind, page).map { it.toResponse() })

    fun getEntry(id: Long): DictionaryEntryResponse = dictionaries.getEntry(kind, id).toResponse()

    fun createEntry(actor: Actor, request: CreateDictionaryEntryRequest): ResponseEntity<DictionaryEntryResponse> {
        val entry = dictionaries.createEntry(actor, kind, request.code, request.title)
        return Responses.created(entry.toResponse(), "$basePath/{id}", entry.id)
    }

    fun updateEntry(actor: Actor, id: Long, request: UpdateDictionaryEntryRequest): DictionaryEntryResponse =
        dictionaries.updateEntry(actor, kind, id, request.title).toResponse()

    fun deleteEntry(actor: Actor, id: Long): ResponseEntity<Void> {
        dictionaries.deleteEntry(actor, kind, id)
        return ResponseEntity.noContent().build()
    }
}
