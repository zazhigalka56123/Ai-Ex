package ru.itmo.aiex.notification.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import ru.itmo.aiex.notification.dto.NotificationResponse
import ru.itmo.aiex.notification.dto.toResponse
import ru.itmo.aiex.notification.service.IdentityClient
import ru.itmo.aiex.notification.service.NotificationCommand
import ru.itmo.aiex.notification.service.NotificationService
import ru.itmo.aiex.notification.web.TraceIdWebFilter
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@RestController
@Tag(name = "Уведомления")
class NotificationController(private val service: NotificationService, private val users: IdentityClient, private val json: JsonMapper) {
    @GetMapping("/internal/metrics")
    fun metrics(): Mono<Map<String, Long>> = service.collectMetrics()

    @PutMapping("/internal/notifications/{id}")
    fun deliver(@PathVariable id: UUID, @RequestBody command: NotificationCommand): Mono<UUID> = service.deliver(id, command)

    @GetMapping("/api/v1/notifications", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "listNotifications",
        summary = "Мои уведомления",
        description = "Не больше 50 записей; общее количество — в X-Total-Count.",
    )
    @ApiResponse(responseCode = "200", description = "Страница уведомлений")
    @ApiResponse(
        responseCode = "400",
        description = "Некорректные параметры запроса",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "401",
        description = "Пользователь не найден или заблокирован",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "503",
        description = "Сервис пользователей недоступен",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "500",
        description = "Внутренняя ошибка сервера",
        content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
    )
    fun getNotifications(
        response: ServerHttpResponse,
        @RequestHeader("X-User-Id", required = false) userId: UUID?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "createdAt,desc") sort: String,
    ): Flux<NotificationResponse> {
        if (userId == null) return Flux.error(ResponseStatusException(HttpStatus.UNAUTHORIZED))
        if (page < 0 || size !in 1..MAX_PAGE_SIZE || sort !in setOf("createdAt,asc", "createdAt,desc")) {
            return Flux.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректная пагинация"))
        }
        return Mono.deferContextual { context ->
            Mono.fromCallable { users.findActive(userId, context.get(TraceIdWebFilter.CONTEXT_KEY)) }
                .subscribeOn(Schedulers.boundedElastic())
        }
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.UNAUTHORIZED)))
            .flatMapMany {
                service.countNotifications(userId).flatMapMany { total ->
                    response.headers.apply {
                        set("X-Total-Count", total.toString())
                        set("X-Total-Pages", ((total + size - 1) / size).toString())
                        set("X-Page", page.toString())
                        set("X-Page-Size", size.toString())
                    }
                    service.findNotifications(userId, size, page.toLong() * size, sort == "createdAt,asc")
                        .map { it.toResponse(json) }
                }
            }
    }

    private companion object {
        const val MAX_PAGE_SIZE = 50
    }
}
