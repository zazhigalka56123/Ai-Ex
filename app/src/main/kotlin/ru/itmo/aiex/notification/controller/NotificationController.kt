package ru.itmo.aiex.notification.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.web.ApiPaths
import ru.itmo.aiex.common.web.PageParams
import ru.itmo.aiex.common.web.Responses
import ru.itmo.aiex.common.web.openapi.ApiErrors
import ru.itmo.aiex.notification.dto.NotificationResponse
import ru.itmo.aiex.notification.dto.toResponse
import ru.itmo.aiex.notification.entity.NotificationStatus
import ru.itmo.aiex.notification.service.NotificationService
import tools.jackson.databind.json.JsonMapper

@RestController
@RequestMapping("${ApiPaths.V1}/notifications")
@Tag(name = "Уведомления", description = "Уведомления текущего пользователя (заготовка под лаб. 4)")
class NotificationController(private val notifications: NotificationService, private val jsonMapper: JsonMapper) {
    @GetMapping
    @Operation(
        operationId = "listNotifications",
        summary = "Мои уведомления",
        description = "Только уведомления текущего пользователя. Offset-пагинация, общее количество - в `X-Total-Count`; " +
            "по умолчанию новые сверху. Фильтр `status`.",
    )
    @ApiResponse(responseCode = "200", description = "Страница уведомлений")
    @ApiErrors(ErrorCode.VALIDATION_FAILED)
    fun list(
        actor: Actor,
        @PageParams(sortable = ["createdAt"], defaultSort = "createdAt,desc") page: PageQuery,
        @RequestParam(required = false) status: NotificationStatus?,
    ): ResponseEntity<List<NotificationResponse>> = Responses.page(notifications.list(actor, status, page).map { it.toResponse(jsonMapper) })
}
