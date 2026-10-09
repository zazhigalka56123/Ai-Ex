package ru.itmo.aiex.admin.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.itmo.aiex.admin.dto.CreateDictionaryEntryRequest
import ru.itmo.aiex.admin.dto.DictionaryEntryResponse
import ru.itmo.aiex.admin.dto.UpdateDictionaryEntryRequest
import ru.itmo.aiex.admin.service.DictionaryAdministration
import ru.itmo.aiex.admin.service.DictionaryKind
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.web.API
import ru.itmo.aiex.common.web.PageParams
import ru.itmo.aiex.common.web.openapi.ApiErrors
@RestController
@RequestMapping("$API/tags")
@Tag(name = "Справочники", description = "Теги персон и специализации специалистов: читают все, ведёт администратор")
class TagController(dictionaries: DictionaryAdministration) {
    private val endpoints = DictionaryEndpoints(DictionaryKind.TAGS, "$API/tags", dictionaries)

    @Suppress("UnusedParameter")
    @GetMapping
    @Operation(operationId = "listTags", summary = "Справочник тегов", description = "По возрастанию кода. Общее количество - в `X-Total-Count`.")
    @ApiResponse(responseCode = "200", description = "Страница тегов")
    @ApiErrors(ErrorCode.VALIDATION_FAILED)
    fun getTags(actor: Actor?, @PageParams page: PageQuery): ResponseEntity<List<DictionaryEntryResponse>> = endpoints.getEntries(page)

    @Suppress("UnusedParameter")
    @GetMapping("/{id}")
    @Operation(operationId = "getTag", summary = "Тег по id")
    @ApiResponse(responseCode = "200", description = "Тег")
    @ApiErrors(ErrorCode.TAG_NOT_FOUND)
    fun getTag(actor: Actor?, @PathVariable id: Long): ResponseEntity<DictionaryEntryResponse> = endpoints.getEntry(id)

    @PostMapping
    @Operation(operationId = "createTag", summary = "Добавить тег", description = "Только администратор. Код приводится к нижнему регистру.")
    @ApiResponse(responseCode = "201", description = "Тег создан", headers = [Header(name = "Location", description = "URI тега")])
    @ApiErrors(ErrorCode.VALIDATION_FAILED, ErrorCode.FORBIDDEN, ErrorCode.DICTIONARY_CODE_TAKEN)
    fun createTag(actor: Actor, @Valid @RequestBody request: CreateDictionaryEntryRequest): ResponseEntity<DictionaryEntryResponse> =
        endpoints.createEntry(actor, request)

    @PatchMapping("/{id}")
    @Operation(operationId = "updateTag", summary = "Переименовать тег", description = "Только администратор. Код не меняется.")
    @ApiResponse(responseCode = "200", description = "Тег изменён")
    @ApiErrors(ErrorCode.VALIDATION_FAILED, ErrorCode.FORBIDDEN, ErrorCode.TAG_NOT_FOUND)
    fun updateTag(
        actor: Actor,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateDictionaryEntryRequest,
    ): ResponseEntity<DictionaryEntryResponse> = endpoints.updateEntry(actor, id, request)

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteTag", summary = "Удалить тег", description = "Только администратор. Тег, назначенный персонам, удалить нельзя.")
    @ApiResponse(responseCode = "204", description = "Тег удалён")
    @ApiErrors(ErrorCode.FORBIDDEN, ErrorCode.TAG_NOT_FOUND, ErrorCode.CONSTRAINT_VIOLATED)
    fun deleteTag(actor: Actor, @PathVariable id: Long): ResponseEntity<Void> = endpoints.deleteEntry(actor, id)
}
