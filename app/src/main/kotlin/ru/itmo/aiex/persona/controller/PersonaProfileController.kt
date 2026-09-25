package ru.itmo.aiex.persona.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.persona.service.PersonaProfileService
import ru.itmo.aiex.persona.service.ProfileRebuildService
import ru.itmo.aiex.persona.dto.ProfileRebuildResponse
import ru.itmo.aiex.persona.dto.ProfileResponse
import ru.itmo.aiex.persona.dto.ProfileVersionResponse
import ru.itmo.aiex.persona.dto.toResponse
import ru.itmo.aiex.common.web.ApiPaths
import ru.itmo.aiex.common.web.PageParams
import ru.itmo.aiex.common.web.Responses
import ru.itmo.aiex.common.web.openapi.ApiErrors
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.V1}/personas/{id}")
@Tag(name = "Профиль персоны", description = "Версионированный профиль: системный промпт, стиль речи, черты")
class PersonaProfileController(private val profiles: PersonaProfileService, private val rebuilds: ProfileRebuildService) {
    @GetMapping("/profile")
    @Operation(
        operationId = "getPersonaProfile",
        summary = "Активный профиль персоны",
        description = "Активная версия профиля своей персоны. Пока профиль не собран (или персона архивна) - `409 PERSONA_NOT_READY`.",
    )
    @ApiResponse(responseCode = "200", description = "Активная версия профиля")
    @ApiErrors(ErrorCode.PERSONA_NOT_FOUND, ErrorCode.PERSONA_NOT_READY)
    fun get(actor: Actor, @PathVariable id: UUID): ProfileResponse = profiles.activeProfile(actor, id).toResponse()

    @GetMapping("/profile/versions")
    @Operation(
        operationId = "listPersonaProfileVersions",
        summary = "Версии профиля",
        description = "История версий профиля своей персоны; активной бывает ровно одна. Offset-пагинация с `X-Total-Count`.",
    )
    @ApiResponse(responseCode = "200", description = "Страница версий")
    @ApiErrors(ErrorCode.PERSONA_NOT_FOUND)
    fun versions(
        actor: Actor,
        @PathVariable id: UUID,
        @PageParams(sortable = ["versionNo", "createdAt"], defaultSort = "versionNo,desc") page: PageQuery,
    ): ResponseEntity<List<ProfileVersionResponse>> = Responses.page(profiles.versions(actor, id, page).map { it.toResponse() })

    @PostMapping("/profile:rebuild")
    @Operation(
        operationId = "rebuildPersonaProfile",
        summary = "Пересобрать профиль",
        description =
        "Повтор сборки профиля из последнего загруженного корпуса - например, после того как LLM был недоступен. " +
            "Идемпотентно: если активная версия уже собрана из последнего корпуса, новая не создаётся. Владелец или администратор.",
    )
    @ApiResponse(
        responseCode = "202",
        description = "Профиль собран и активирован",
        headers = [Header(name = "Location", description = "URI профиля")],
    )
    @ApiErrors(
        ErrorCode.PERSONA_NOT_FOUND,
        ErrorCode.PERSONA_INVALID_STATE,
        ErrorCode.PERSONA_NOT_READY,
        ErrorCode.CONCURRENT_MODIFICATION,
        ErrorCode.LLM_UNAVAILABLE,
    )
    fun rebuild(actor: Actor, @PathVariable id: UUID): ResponseEntity<ProfileRebuildResponse> {
        val result = rebuilds.rebuildManually(actor, id)
        return Responses.accepted(result.toResponse(), "${ApiPaths.V1}/personas/{id}/profile", id)
    }
}
