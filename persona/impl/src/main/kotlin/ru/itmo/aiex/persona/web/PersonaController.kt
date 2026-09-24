package ru.itmo.aiex.persona.web

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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.persona.application.CreatePersonaCommand
import ru.itmo.aiex.persona.application.PersonaService
import ru.itmo.aiex.persona.application.TagAssignment
import ru.itmo.aiex.persona.application.UpdatePersonaCommand
import ru.itmo.aiex.persona.domain.PersonaStatus
import ru.itmo.aiex.persona.web.dto.CreatePersonaRequest
import ru.itmo.aiex.persona.web.dto.PersonaResponse
import ru.itmo.aiex.persona.web.dto.PersonaSummaryResponse
import ru.itmo.aiex.persona.web.dto.ReplaceTagsRequest
import ru.itmo.aiex.persona.web.dto.UpdatePersonaRequest
import ru.itmo.aiex.persona.web.dto.toResponse
import ru.itmo.aiex.persona.web.dto.toSummaryResponse
import ru.itmo.aiex.web.ApiPaths
import ru.itmo.aiex.web.PageParams
import ru.itmo.aiex.web.Responses
import ru.itmo.aiex.web.openapi.ApiErrors
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.V1}/personas")
@Tag(name = "Персоны", description = "Цифровые персоны пользователя: создание, редактирование, теги, архивация")
class PersonaController(private val personas: PersonaService) {
    @PostMapping
    @Operation(
        operationId = "createPersona",
        summary = "Создать персону",
        description = "Только роль USER. Персона создаётся в статусе `DRAFT`; теги из `tagCodes` ставятся как ручные с весом 1.0.",
    )
    @ApiResponse(responseCode = "201", description = "Персона создана", headers = [Header(name = "Location", description = "URI персоны")])
    @ApiErrors(ErrorCode.VALIDATION_FAILED, ErrorCode.FORBIDDEN)
    fun create(actor: Actor, @Valid @RequestBody request: CreatePersonaRequest): ResponseEntity<PersonaResponse> {
        val persona = personas.create(actor, CreatePersonaCommand(request.name, request.relationshipKind, request.description, request.tagCodes))
        return Responses.created(persona.toResponse(), "${ApiPaths.V1}/personas/{id}", persona.id)
    }

    @GetMapping
    @Operation(
        operationId = "listPersonas",
        summary = "Мои персоны",
        description =
        "Только свои персоны. Offset-пагинация, **общее количество - в `X-Total-Count`** (плюс `X-Total-Pages` и `Link`). " +
            "Без `status` архивные персоны не показываются; `status=ARCHIVED` - только архивные.",
    )
    @ApiResponse(responseCode = "200", description = "Страница персон")
    fun list(
        actor: Actor,
        @PageParams(sortable = ["createdAt", "updatedAt", "name"], defaultSort = "createdAt,desc") page: PageQuery,
        @RequestParam(required = false) status: PersonaStatus?,
    ): ResponseEntity<List<PersonaSummaryResponse>> = Responses.page(personas.list(actor, status, page).map { it.toSummaryResponse() })

    @GetMapping("/{id}")
    @Operation(operationId = "getPersona", summary = "Персона по id", description = "Только своя: чужая и несуществующая неотличимы (`404`).")
    @ApiResponse(responseCode = "200", description = "Персона с чертами, тегами и активной версией профиля")
    @ApiErrors(ErrorCode.PERSONA_NOT_FOUND)
    fun get(actor: Actor, @PathVariable id: UUID): PersonaResponse = personas.get(actor, id).toResponse()

    @PatchMapping("/{id}")
    @Operation(
        operationId = "updatePersona",
        summary = "Изменить персону",
        description = "Имя, тип отношений, описание. Архивную персону изменить нельзя (`409`).",
    )
    @ApiResponse(responseCode = "200", description = "Персона изменена")
    @ApiErrors(ErrorCode.VALIDATION_FAILED, ErrorCode.PERSONA_NOT_FOUND, ErrorCode.PERSONA_INVALID_STATE, ErrorCode.CONCURRENT_MODIFICATION)
    fun update(actor: Actor, @PathVariable id: UUID, @Valid @RequestBody request: UpdatePersonaRequest): PersonaResponse =
        personas.update(actor, id, UpdatePersonaCommand(request.name, request.relationshipKind, request.description)).toResponse()

    @DeleteMapping("/{id}")
    @Operation(
        operationId = "archivePersona",
        summary = "Архивировать персону",
        description =
        "Soft-delete (TX-4): статус `ARCHIVED`, профиль деактивируется, теги снимаются, сырые сообщения импортов удаляются. " +
            "Владелец или администратор (любую персону). Повторный вызов - тоже `204`.",
    )
    @ApiResponse(responseCode = "204", description = "Персона архивирована")
    @ApiErrors(ErrorCode.PERSONA_NOT_FOUND, ErrorCode.CONCURRENT_MODIFICATION)
    fun archive(actor: Actor, @PathVariable id: UUID): ResponseEntity<Void> {
        personas.archive(id, actor)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/{id}/tags")
    @Operation(
        operationId = "replacePersonaTags",
        summary = "Заменить ручные теги",
        description =
        "Заменяет набор ручных тегов (до 10). Автотеги из анализа переписки остаются; автотег, присланный явно, становится ручным.",
    )
    @ApiResponse(responseCode = "200", description = "Персона с новым набором тегов")
    @ApiErrors(ErrorCode.VALIDATION_FAILED, ErrorCode.PERSONA_NOT_FOUND, ErrorCode.PERSONA_INVALID_STATE, ErrorCode.CONCURRENT_MODIFICATION)
    fun replaceTags(actor: Actor, @PathVariable id: UUID, @Valid @RequestBody request: ReplaceTagsRequest): PersonaResponse =
        personas.replaceTags(actor, id, request.tags.map { TagAssignment(it.code, it.weight) }).toResponse()
}
