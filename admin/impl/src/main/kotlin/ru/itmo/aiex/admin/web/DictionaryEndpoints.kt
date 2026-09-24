package ru.itmo.aiex.admin.web

import org.springframework.http.ResponseEntity
import ru.itmo.aiex.admin.application.DictionaryAdministration
import ru.itmo.aiex.admin.application.DictionaryKind
import ru.itmo.aiex.admin.web.dto.CreateDictionaryEntryRequest
import ru.itmo.aiex.admin.web.dto.DictionaryEntryResponse
import ru.itmo.aiex.admin.web.dto.UpdateDictionaryEntryRequest
import ru.itmo.aiex.admin.web.dto.toResponse
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.web.Responses

class DictionaryEndpoints(private val kind: DictionaryKind, private val basePath: String, private val dictionaries: DictionaryAdministration) {
    fun list(page: PageQuery): ResponseEntity<List<DictionaryEntryResponse>> = Responses.page(dictionaries.list(kind, page).map { it.toResponse() })

    fun get(id: Long): DictionaryEntryResponse = dictionaries.get(kind, id).toResponse()

    fun create(actor: Actor, request: CreateDictionaryEntryRequest): ResponseEntity<DictionaryEntryResponse> {
        val entry = dictionaries.create(actor, kind, request.code, request.title)
        return Responses.created(entry.toResponse(), "$basePath/{id}", entry.id)
    }

    fun update(actor: Actor, id: Long, request: UpdateDictionaryEntryRequest): DictionaryEntryResponse =
        dictionaries.update(actor, kind, id, request.title).toResponse()

    fun delete(actor: Actor, id: Long): ResponseEntity<Void> {
        dictionaries.delete(actor, kind, id)
        return ResponseEntity.noContent().build()
    }
}
