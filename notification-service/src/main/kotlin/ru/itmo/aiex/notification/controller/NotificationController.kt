package ru.itmo.aiex.notification.controller

import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import ru.itmo.aiex.notification.entity.Notification
import ru.itmo.aiex.notification.entity.NotificationStatus
import ru.itmo.aiex.notification.repository.NotificationRepository
import ru.itmo.aiex.notification.service.IdentityClient
import ru.itmo.aiex.notification.service.NotificationCommand
import ru.itmo.aiex.notification.service.NotificationService
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@RestController
class NotificationController(
    private val notifications: NotificationRepository,
    private val service: NotificationService,
    private val users: IdentityClient,
    private val json: JsonMapper,
) {
    @GetMapping("/internal/metrics")
    fun metrics(): Mono<Map<String, Long>> = reactor.core.publisher.Flux.fromArray(NotificationStatus.entries.toTypedArray())
        .flatMap { status -> notifications.countByStatus(status).map { "notifications.${status.name.lowercase()}" to it } }
        .collectMap({ it.first }, { it.second })

    @PutMapping("/internal/notifications/{id}")
    fun deliver(@PathVariable id: UUID, @RequestBody command: NotificationCommand): Mono<UUID> = service.deliver(id, command)

    @GetMapping("/api/v1/notifications")
    fun list(
        response: ServerHttpResponse,
        @RequestHeader("X-User-Id", required = false) userId: UUID?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "createdAt,desc") sort: String,
        @RequestParam(required = false) status: NotificationStatus?,
    ): Mono<List<Map<String, Any?>>> {
        if (userId == null) return Mono.error(ResponseStatusException(HttpStatus.UNAUTHORIZED))
        if (page < 0 || size !in 1..MAX_PAGE_SIZE || sort !in setOf("createdAt,asc", "createdAt,desc")) {
            return Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректная пагинация"))
        }
        return Mono.fromCallable { users.findActive(userId) }
            .subscribeOn(Schedulers.boundedElastic())
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.UNAUTHORIZED)))
            .flatMap {
                val rows = if (sort.endsWith("asc")) {
                    notifications.pageAscending(userId, status?.name, size, page.toLong() * size)
                } else {
                    notifications.page(userId, status?.name, size, page.toLong() * size)
                }
                Mono.zip(rows.map(::response).collectList(), notifications.count(userId, status?.name))
            }.map { result ->
                response.headers.apply {
                    set("X-Total-Count", result.t2.toString())
                    set("X-Total-Pages", ((result.t2 + size - 1) / size).toString())
                    set("X-Page", page.toString())
                    set("X-Page-Size", size.toString())
                }
                result.t1
            }
    }

    private fun response(value: Notification): Map<String, Any?> = mapOf(
        "id" to value.id,
        "type" to value.type,
        "status" to value.status,
        "payload" to json.readTree(value.payload),
        "attempts" to value.attempts,
        "createdAt" to value.createdAt,
        "sentAt" to value.sentAt,
    )
    private companion object {
        const val MAX_PAGE_SIZE = 50
    }
}
