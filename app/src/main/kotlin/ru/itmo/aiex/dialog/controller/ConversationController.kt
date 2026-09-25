package ru.itmo.aiex.dialog.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.dialog.service.ConversationService
import ru.itmo.aiex.dialog.service.CreateConversationCommand
import ru.itmo.aiex.dialog.entity.ConversationStatus
import ru.itmo.aiex.dialog.dto.ConversationResponse
import ru.itmo.aiex.dialog.dto.CreateConversationRequest
import ru.itmo.aiex.dialog.dto.toResponse
import ru.itmo.aiex.common.web.ApiPaths
import ru.itmo.aiex.common.web.PageParams
import ru.itmo.aiex.common.web.Responses
import ru.itmo.aiex.common.web.openapi.ApiErrors
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.V1}/conversations")
@Tag(name = "Беседы", description = "Беседы клиента с его ИИ-персонами")
class ConversationController(private val conversations: ConversationService) {
    @PostMapping
    @Operation(
        operationId = "createConversation",
        summary = "Начать беседу с персоной",
        description = "Только со своей персоной в статусе `READY`, у которой есть активный профиль. " +
            "Чужая или несуществующая персона - `404`, неготовая - `409 PERSONA_NOT_READY`.",
    )
    @ApiResponse(responseCode = "201", description = "Беседа создана", headers = [Header(name = "Location", description = "URI беседы")])
    @ApiErrors(ErrorCode.VALIDATION_FAILED, ErrorCode.PERSONA_NOT_FOUND, ErrorCode.PERSONA_NOT_READY)
    fun create(actor: Actor, @Valid @RequestBody request: CreateConversationRequest): ResponseEntity<ConversationResponse> {
        val conversation = conversations.create(actor, CreateConversationCommand(request.personaId, request.title))
        return Responses.created(conversation.toResponse(), "${ApiPaths.V1}/conversations/{id}", conversation.id)
    }

    @GetMapping
    @Operation(
        operationId = "listConversations",
        summary = "Мои беседы",
        description = "Offset-пагинация, общее количество - в `X-Total-Count`. По умолчанию свежие сверху (`lastMessageAt,desc`; " +
            "беседы без сообщений идут первыми). Фильтры: `status`, `personaId`.",
    )
    @ApiResponse(responseCode = "200", description = "Страница бесед")
    @ApiErrors(ErrorCode.VALIDATION_FAILED)
    fun list(
        actor: Actor,
        @PageParams(sortable = ["lastMessageAt", "createdAt"], defaultSort = "lastMessageAt,desc") page: PageQuery,
        @RequestParam(required = false) status: ConversationStatus?,
        @RequestParam(required = false) personaId: UUID?,
    ): ResponseEntity<List<ConversationResponse>> = Responses.page(conversations.list(actor, status, personaId, page).map { it.toResponse() })

    @GetMapping("/{id}")
    @Operation(
        operationId = "getConversation",
        summary = "Беседа по id",
        description = "Владельцу и специалисту, которому клиент расшарил беседу в активной консультации. " +
            "Специалисту без расшаривания - `403`, остальным - `404`.",
    )
    @ApiResponse(responseCode = "200", description = "Беседа")
    @ApiErrors(ErrorCode.FORBIDDEN, ErrorCode.CONVERSATION_NOT_FOUND)
    fun get(actor: Actor, @PathVariable id: UUID): ConversationResponse = conversations.get(actor, id).toResponse()

    @DeleteMapping("/{id}")
    @Operation(
        operationId = "archiveConversation",
        summary = "Архивировать беседу",
        description = "Только владелец. Soft-delete: беседа переходит в `ARCHIVED`, история остаётся доступной для чтения, " +
            "новые сообщения не принимаются. Повторный вызов - тоже `204`.",
    )
    @ApiResponse(responseCode = "204", description = "Беседа в архиве")
    @ApiErrors(ErrorCode.CONVERSATION_NOT_FOUND, ErrorCode.CONCURRENT_MODIFICATION)
    fun archive(actor: Actor, @PathVariable id: UUID): ResponseEntity<Void> {
        conversations.archive(actor, id)
        return ResponseEntity.noContent().build()
    }
}
