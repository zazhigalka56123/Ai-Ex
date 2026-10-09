package ru.itmo.aiex.notification.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.web.AiExHeaders
import ru.itmo.aiex.common.web.ApiPaths
import ru.itmo.aiex.common.web.PageParams
import ru.itmo.aiex.notification.dto.NotificationResponse
import ru.itmo.aiex.notification.dto.toResponse
import ru.itmo.aiex.notification.entity.NotificationStatus
import ru.itmo.aiex.notification.service.NotificationService
import ru.itmo.aiex.reactive.ReactiveResponses
import tools.jackson.databind.json.JsonMapper

@RestController
@RequestMapping("${ApiPaths.V1}/notifications")
@Tag(name = "Уведомления", description = "Уведомления текущего пользователя об импортах, персонах, консультациях и модерации")
class NotificationController(private val notifications: NotificationService, private val mapper: JsonMapper) {
    @GetMapping
    @Operation(
        operationId = "listNotifications",
        summary = "Мои уведомления",
        description = "Offset-пагинация, новые сверху; общее количество - в `X-Total-Count`.",
        parameters = [
            Parameter(name = AiExHeaders.USER_ID, `in` = ParameterIn.HEADER, required = true),
            Parameter(name = "page", `in` = ParameterIn.QUERY, description = "Номер страницы с 0"),
            Parameter(name = "size", `in` = ParameterIn.QUERY, description = "Размер страницы, не больше 50"),
            Parameter(name = "sort", `in` = ParameterIn.QUERY, description = "createdAt,asc|desc"),
        ],
    )
    @ApiResponse(responseCode = "200", description = "Страница уведомлений")
    @ApiResponse(responseCode = "400", description = "Некорректные параметры страницы или статуса")
    @ApiResponse(responseCode = "401", description = "Пользователь не определён")
    @ApiResponse(responseCode = "503", description = "account-service недоступен")
    fun getNotifications(
        @Parameter(hidden = true) actor: Actor,
        @Parameter(hidden = true) @PageParams(sortable = ["createdAt"], defaultSort = "createdAt,desc") page: PageQuery,
        @RequestParam(required = false) status: NotificationStatus?,
        request: ServerHttpRequest,
    ): Mono<ResponseEntity<List<NotificationResponse>>> = notifications.getNotifications(actor.userId, status, page)
        .map { result -> ReactiveResponses.page(result.map { it.toResponse(mapper) }, request) }
}
