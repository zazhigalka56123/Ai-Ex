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
@RequestMapping("$API/specializations")
@Tag(name = "Справочники", description = "Теги персон и специализации специалистов: читают все, ведёт администратор")
class SpecializationController(dictionaries: DictionaryAdministration) {
    private val endpoints = DictionaryEndpoints(DictionaryKind.SPECIALIZATIONS, "$API/specializations", dictionaries)

    @Suppress("UnusedParameter")
    @GetMapping
    @Operation(
        operationId = "listSpecializations",
        summary = "Справочник специализаций",
        description = "По возрастанию кода. Общее количество - в `X-Total-Count`.",
    )
    @ApiResponse(responseCode = "200", description = "Страница специализаций")
    @ApiErrors(ErrorCode.VALIDATION_FAILED)
    fun getSpecializations(actor: Actor?, @PageParams page: PageQuery): ResponseEntity<List<DictionaryEntryResponse>> = endpoints.getEntries(page)

    @Suppress("UnusedParameter")
    @GetMapping("/{id}")
    @Operation(operationId = "getSpecialization", summary = "Специализация по id")
    @ApiResponse(responseCode = "200", description = "Специализация")
    @ApiErrors(ErrorCode.SPECIALIZATION_NOT_FOUND)
    fun getSpecialization(actor: Actor?, @PathVariable id: Long): ResponseEntity<DictionaryEntryResponse> = endpoints.getEntry(id)

    @PostMapping
    @Operation(
        operationId = "createSpecialization",
        summary = "Добавить специализацию",
        description = "Только администратор. Код - латиница, цифры и дефис, приводится к нижнему регистру.",
    )
    @ApiResponse(
        responseCode = "201",
        description = "Специализация создана",
        headers = [Header(name = "Location", description = "URI специализации")],
    )
    @ApiErrors(ErrorCode.VALIDATION_FAILED, ErrorCode.FORBIDDEN, ErrorCode.DICTIONARY_CODE_TAKEN)
    fun createSpecialization(actor: Actor, @Valid @RequestBody request: CreateDictionaryEntryRequest): ResponseEntity<DictionaryEntryResponse> =
        endpoints.createEntry(actor, request)

    @PatchMapping("/{id}")
    @Operation(operationId = "updateSpecialization", summary = "Переименовать специализацию", description = "Только администратор. Код не меняется.")
    @ApiResponse(responseCode = "200", description = "Специализация изменена")
    @ApiErrors(ErrorCode.VALIDATION_FAILED, ErrorCode.FORBIDDEN, ErrorCode.SPECIALIZATION_NOT_FOUND)
    fun updateSpecialization(
        actor: Actor,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateDictionaryEntryRequest,
    ): ResponseEntity<DictionaryEntryResponse> = endpoints.updateEntry(actor, id, request)

    @DeleteMapping("/{id}")
    @Operation(
        operationId = "deleteSpecialization",
        summary = "Удалить специализацию",
        description = "Только администратор. Специализацию, указанную в профилях специалистов, удалить нельзя.",
    )
    @ApiResponse(responseCode = "204", description = "Специализация удалена")
    @ApiErrors(ErrorCode.FORBIDDEN, ErrorCode.SPECIALIZATION_NOT_FOUND, ErrorCode.CONSTRAINT_VIOLATED)
    fun deleteSpecialization(actor: Actor, @PathVariable id: Long): ResponseEntity<Void> = endpoints.deleteEntry(actor, id)
}
