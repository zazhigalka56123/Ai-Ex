package ru.itmo.aiex.care.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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
import ru.itmo.aiex.care.dto.CreateSlotRequest
import ru.itmo.aiex.care.dto.CreateSpecialistRequest
import ru.itmo.aiex.care.dto.SlotResponse
import ru.itmo.aiex.care.dto.SpecialistResponse
import ru.itmo.aiex.care.dto.UpdateSpecialistRequest
import ru.itmo.aiex.care.dto.toResponse
import ru.itmo.aiex.care.service.CreateSlotCommand
import ru.itmo.aiex.care.service.CreateSpecialistCommand
import ru.itmo.aiex.care.service.SlotService
import ru.itmo.aiex.care.service.SpecialistDirectory
import ru.itmo.aiex.care.service.UpdateSpecialistCommand
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.web.ApiPaths
import ru.itmo.aiex.common.web.PageParams
import ru.itmo.aiex.common.web.Responses
import ru.itmo.aiex.common.web.openapi.ApiErrors
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.V1}/specialists")
@Tag(name = "Специалисты", description = "Профили специалистов, каталог с фильтром по специализации, расписание слотов")
class SpecialistController(private val directory: SpecialistDirectory, private val slots: SlotService) {
    @PostMapping
    @Operation(
        operationId = "createSpecialist",
        summary = "Создать свой профиль специалиста",
        description = "Только роль SPECIALIST, один профиль на пользователя. Специализации - коды из справочника `/specializations`.",
    )
    @ApiResponse(responseCode = "201", description = "Профиль создан", headers = [Header(name = "Location", description = "URI профиля")])
    @ApiErrors(ErrorCode.VALIDATION_FAILED, ErrorCode.FORBIDDEN, ErrorCode.SPECIALIST_PROFILE_EXISTS)
    fun create(actor: Actor, @Valid @RequestBody request: CreateSpecialistRequest): ResponseEntity<SpecialistResponse> {
        val card = directory.create(actor, CreateSpecialistCommand(request.headline, request.bio, request.pricePerHour, request.specializationCodes))
        return Responses.created(card.toResponse(), "${ApiPaths.V1}/specialists/{id}", card.specialist.id)
    }

    @Suppress("UnusedParameter")
    @GetMapping
    @Operation(
        operationId = "listSpecialists",
        summary = "Каталог специалистов",
        description = "Только активные профили. Фильтр `specialization` - код специализации. Offset-пагинация, общее количество - в `X-Total-Count`.",
    )
    @ApiResponse(responseCode = "200", description = "Страница каталога")
    @ApiErrors(ErrorCode.VALIDATION_FAILED)
    fun list(
        actor: Actor?,
        @Parameter(description = "Код специализации, например `breakup`") @RequestParam(required = false) specialization: String?,
        @PageParams(sortable = ["pricePerHour", "createdAt"], defaultSort = "createdAt,desc") page: PageQuery,
    ): ResponseEntity<List<SpecialistResponse>> = Responses.page(directory.catalog(specialization, page).map { it.toResponse() })

    @GetMapping("/{id}")
    @Operation(
        operationId = "getSpecialist",
        summary = "Профиль специалиста",
        description = "Активный профиль виден всем, неактивный - только владельцу и администратору.",
    )
    @ApiResponse(responseCode = "200", description = "Профиль специалиста")
    @ApiErrors(ErrorCode.SPECIALIST_NOT_FOUND)
    fun get(actor: Actor?, @PathVariable id: UUID): SpecialistResponse = directory.get(actor, id).toResponse()

    @PatchMapping("/{id}")
    @Operation(
        operationId = "updateSpecialist",
        summary = "Изменить профиль специалиста",
        description = "Свой профиль или любой - администратору. `status = INACTIVE` скрывает профиль из каталога.",
    )
    @ApiResponse(responseCode = "200", description = "Профиль изменён")
    @ApiErrors(ErrorCode.VALIDATION_FAILED, ErrorCode.FORBIDDEN, ErrorCode.SPECIALIST_NOT_FOUND, ErrorCode.CONCURRENT_MODIFICATION)
    fun update(actor: Actor, @PathVariable id: UUID, @Valid @RequestBody request: UpdateSpecialistRequest): SpecialistResponse {
        val command = UpdateSpecialistCommand(request.headline, request.bio, request.pricePerHour, request.status, request.specializationCodes)
        return directory.update(actor, id, command).toResponse()
    }

    @PostMapping("/{id}/slots")
    @Operation(
        operationId = "createSlot",
        summary = "Опубликовать слот",
        description = "Только владелец профиля (роль SPECIALIST). Слот в будущем, 15–240 минут, не пересекается с другими слотами специалиста.",
    )
    @ApiResponse(responseCode = "201", description = "Слот опубликован", headers = [Header(name = "Location", description = "URI слота")])
    @ApiErrors(ErrorCode.VALIDATION_FAILED, ErrorCode.FORBIDDEN, ErrorCode.SPECIALIST_NOT_FOUND, ErrorCode.SLOT_OVERLAP)
    fun createSlot(actor: Actor, @PathVariable id: UUID, @Valid @RequestBody request: CreateSlotRequest): ResponseEntity<SlotResponse> {
        val slot = slots.create(actor, id, CreateSlotCommand(request.startsAt, request.durationMin))
        return Responses.created(slot.toResponse(), "${ApiPaths.V1}/specialists/{id}/slots/{slotId}", id, slot.id)
    }

    @GetMapping("/{id}/slots")
    @Operation(
        operationId = "listFreeSlots",
        summary = "Свободные слоты специалиста",
        description = "Будущие слоты без активной записи в окне `[from, to)`. Offset-пагинация, общее количество - в `X-Total-Count`.",
    )
    @ApiResponse(responseCode = "200", description = "Страница свободных слотов")
    @ApiErrors(ErrorCode.VALIDATION_FAILED, ErrorCode.SPECIALIST_NOT_FOUND)
    fun listFreeSlots(
        actor: Actor?,
        @PathVariable id: UUID,
        @Parameter(description = "Начало окна (ISO-8601), по умолчанию - сейчас") @RequestParam(required = false) from: Instant?,
        @Parameter(description = "Конец окна (ISO-8601, не включая)") @RequestParam(required = false) to: Instant?,
        @PageParams(sortable = ["startsAt"], defaultSort = "startsAt,asc") page: PageQuery,
    ): ResponseEntity<List<SlotResponse>> = Responses.page(slots.listFree(actor, id, from, to, page).map { it.toResponse() })

    @GetMapping("/{id}/slots/{slotId}")
    @Operation(operationId = "getSlot", summary = "Слот специалиста", description = "Слот по id - независимо от того, занят ли он.")
    @ApiResponse(responseCode = "200", description = "Слот")
    @ApiErrors(ErrorCode.SPECIALIST_NOT_FOUND, ErrorCode.SLOT_NOT_FOUND)
    fun getSlot(actor: Actor?, @PathVariable id: UUID, @PathVariable slotId: UUID): SlotResponse = slots.get(actor, id, slotId).toResponse()
}
