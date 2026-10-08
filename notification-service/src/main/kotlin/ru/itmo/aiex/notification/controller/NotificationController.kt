package ru.itmo.aiex.notification.controller

import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import ru.itmo.aiex.notification.dto.NotificationResponse
import ru.itmo.aiex.notification.dto.toResponse
import ru.itmo.aiex.notification.entity.NotificationStatus
import ru.itmo.aiex.notification.service.NotificationService
import ru.itmo.aiex.notification.web.NotificationActorResolver
import ru.itmo.aiex.notification.web.NotificationPaging
import tools.jackson.databind.json.JsonMapper

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notifications: NotificationService,
    private val actors: NotificationActorResolver,
    private val mapper: JsonMapper,
) {
    @GetMapping
    fun getNotifications(
        @RequestHeader("X-User-Id", required = false) userId: String?,
        @RequestParam(required = false) status: NotificationStatus?,
        request: ServerHttpRequest,
    ): Mono<ResponseEntity<List<NotificationResponse>>> = actors.resolve(userId).flatMap { actor ->
        notifications.list(actor.userId, status, NotificationPaging.parse(request))
            .map { page -> NotificationPaging.response(page.map { it.toResponse(mapper) }, request) }
    }
}
