package ru.itmo.aiex.admin.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.itmo.aiex.admin.service.ModerationDesk
import ru.itmo.aiex.admin.service.ReportMessageCommand
import ru.itmo.aiex.admin.service.ReviewFlagCommand
import ru.itmo.aiex.admin.entity.FlagStatus
import ru.itmo.aiex.admin.dto.CreateFlagRequest
import ru.itmo.aiex.admin.dto.FlagResponse
import ru.itmo.aiex.admin.dto.ReviewFlagRequest
import ru.itmo.aiex.admin.dto.toResponse
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.moderation.FlagReason
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.web.ApiPaths
import ru.itmo.aiex.common.web.PageParams
import ru.itmo.aiex.common.web.Responses
import ru.itmo.aiex.common.web.openapi.ApiErrors
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.V1}/moderation/flags")
@Tag(name = "Модерация", description = "Жалобы на сообщения и очередь флагов администратора")
class ModerationController(private val desk: ModerationDesk) {
    @PostMapping
    @Operation(
        operationId = "createFlag",
        summary = "Пожаловаться на сообщение",
        description = "Клиент или специалист - только на сообщение, которое он видит. Повторная жалоба на то же сообщение - `409`.",
    )
    @ApiResponse(responseCode = "201", description = "Флаг создан", headers = [Header(name = "Location", description = "URI флага")])
    @ApiErrors(ErrorCode.VALIDATION_FAILED, ErrorCode.FORBIDDEN, ErrorCode.MESSAGE_NOT_FOUND, ErrorCode.FLAG_ALREADY_REPORTED)
    fun create(actor: Actor, @Valid @RequestBody request: CreateFlagRequest): ResponseEntity<FlagResponse> {
        val flag = desk.report(actor, ReportMessageCommand(request.messageId, request.reason, request.comment))
        return Responses.created(flag.toResponse(), "${ApiPaths.V1}/moderation/flags/{id}", flag.id)
    }

    @GetMapping
    @Operation(
        operationId = "listFlags",
        summary = "Очередь модерации",
        description = "Только администратор. У каждого флага - превью флагнутого сообщения. Общее количество - в `X-Total-Count`.",
    )
    @ApiResponse(responseCode = "200", description = "Страница флагов")
    @ApiErrors(ErrorCode.VALIDATION_FAILED, ErrorCode.FORBIDDEN)
    fun list(
        actor: Actor,
        @RequestParam(required = false) status: FlagStatus?,
        @RequestParam(required = false) reason: FlagReason?,
        @PageParams(sortable = ["createdAt", "updatedAt"], defaultSort = "createdAt,desc") page: PageQuery,
    ): ResponseEntity<List<FlagResponse>> = Responses.page(desk.list(actor, status, reason, page).map { it.toResponse() })

    @GetMapping("/{id}")
    @Operation(operationId = "getFlag", summary = "Флаг по id", description = "Только администратор, с превью флагнутого сообщения.")
    @ApiResponse(responseCode = "200", description = "Флаг")
    @ApiErrors(ErrorCode.FORBIDDEN, ErrorCode.FLAG_NOT_FOUND)
    fun get(actor: Actor, @PathVariable id: UUID): FlagResponse = desk.get(actor, id).toResponse()

    @PatchMapping("/{id}")
    @Operation(
        operationId = "reviewFlag",
        summary = "Вынести вердикт по флагу",
        description =
        "Только администратор. OPEN -> IN_REVIEW | RESOLVED | REJECTED, IN_REVIEW -> RESOLVED | REJECTED. " +
            "`archivePersona` вместе с RESOLVED архивирует персону сообщения после фиксации вердикта.",
    )
    @ApiResponse(responseCode = "200", description = "Вердикт зафиксирован")
    @ApiErrors(
        ErrorCode.VALIDATION_FAILED,
        ErrorCode.FORBIDDEN,
        ErrorCode.FLAG_NOT_FOUND,
        ErrorCode.FLAG_INVALID_STATE,
        ErrorCode.CONCURRENT_MODIFICATION,
    )
    fun review(actor: Actor, @PathVariable id: UUID, @Valid @RequestBody request: ReviewFlagRequest): FlagResponse =
        desk.review(actor, id, ReviewFlagCommand(request.status, request.resolution, request.archivePersona)).toResponse()
}
