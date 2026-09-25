package ru.itmo.aiex.care.controller

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
import ru.itmo.aiex.care.service.BookConsultationCommand
import ru.itmo.aiex.care.service.ConsultationBooking
import ru.itmo.aiex.care.service.ConsultationService
import ru.itmo.aiex.care.entity.SessionStatus
import ru.itmo.aiex.care.dto.BookConsultationRequest
import ru.itmo.aiex.care.dto.ConsultationResponse
import ru.itmo.aiex.care.dto.UpdateConsultationRequest
import ru.itmo.aiex.care.dto.toResponse
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.web.ApiPaths
import ru.itmo.aiex.common.web.PageParams
import ru.itmo.aiex.common.web.Responses
import ru.itmo.aiex.common.web.openapi.ApiErrors
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.V1}/consultations")
@Tag(name = "Консультации", description = "Запись на слот специалиста, подтверждение, проведение, отмена, резюме и оценка")
class ConsultationController(private val booking: ConsultationBooking, private val consultations: ConsultationService) {
    @PostMapping
    @Operation(
        operationId = "bookConsultation",
        summary = "Записаться на консультацию",
        description =
        "Роль USER. TX-3: слот блокируется `SELECT … FOR UPDATE`, двойная запись невозможна - второй клиент получает `409 SLOT_TAKEN`. " +
            "`sharedConversationId` - своя беседа, которую увидит специалист, пока консультация активна.",
    )
    @ApiResponse(responseCode = "201", description = "Запись создана", headers = [Header(name = "Location", description = "URI консультации")])
    @ApiErrors(ErrorCode.VALIDATION_FAILED, ErrorCode.FORBIDDEN, ErrorCode.SLOT_NOT_FOUND, ErrorCode.CONVERSATION_NOT_FOUND, ErrorCode.SLOT_TAKEN)
    fun book(actor: Actor, @Valid @RequestBody request: BookConsultationRequest): ResponseEntity<ConsultationResponse> {
        val session = booking.book(actor, BookConsultationCommand(request.slotId, request.sharedConversationId))
        return Responses.created(session.toResponse(), "${ApiPaths.V1}/consultations/{id}", session.id)
    }

    @GetMapping
    @Operation(
        operationId = "listConsultations",
        summary = "Мои консультации",
        description = "Консультации, где текущий пользователь - клиент или специалист. Offset-пагинация, общее количество - в `X-Total-Count`.",
    )
    @ApiResponse(responseCode = "200", description = "Страница консультаций")
    @ApiErrors(ErrorCode.VALIDATION_FAILED)
    fun list(
        actor: Actor,
        @RequestParam(required = false) status: SessionStatus?,
        @PageParams(sortable = ["startsAt", "createdAt"], defaultSort = "startsAt,desc") page: PageQuery,
    ): ResponseEntity<List<ConsultationResponse>> = Responses.page(consultations.list(actor, status, page).map { it.toResponse() })

    @GetMapping("/{id}")
    @Operation(operationId = "getConsultation", summary = "Консультация по id", description = "Клиенту, специалисту консультации и администратору.")
    @ApiResponse(responseCode = "200", description = "Консультация")
    @ApiErrors(ErrorCode.CONSULTATION_NOT_FOUND)
    fun get(actor: Actor, @PathVariable id: UUID): ConsultationResponse = consultations.get(actor, id).toResponse()

    @PatchMapping("/{id}")
    @Operation(
        operationId = "updateConsultation",
        summary = "Изменить консультацию",
        description =
        "Клиент: отмена из REQUESTED/CONFIRMED, оценка 1–5 после DONE. Специалист: REQUESTED -> CONFIRMED -> DONE, отмена, " +
            "резюме и рекомендации в CONFIRMED/DONE. Администратор: закрытие (DONE) или отмена активной консультации. " +
            "Действие, недоступное роли, - `403`; недопустимое в текущем статусе - `409`.",
    )
    @ApiResponse(responseCode = "200", description = "Консультация изменена")
    @ApiErrors(
        ErrorCode.VALIDATION_FAILED,
        ErrorCode.FORBIDDEN,
        ErrorCode.CONSULTATION_NOT_FOUND,
        ErrorCode.CONSULTATION_INVALID_STATE,
        ErrorCode.CONCURRENT_MODIFICATION,
    )
    fun update(actor: Actor, @PathVariable id: UUID, @Valid @RequestBody request: UpdateConsultationRequest): ConsultationResponse =
        consultations.update(actor, id, request.toChange()).toResponse()
}
