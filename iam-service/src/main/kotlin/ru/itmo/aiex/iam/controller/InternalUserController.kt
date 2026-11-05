package ru.itmo.aiex.iam.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import ru.itmo.aiex.iam.dto.UserView
import ru.itmo.aiex.iam.service.IamMetrics
import ru.itmo.aiex.iam.service.UserQuery
import java.util.UUID

@RestController
class InternalUserController(private val users: UserQuery, private val metrics: IamMetrics) {
    @GetMapping("/internal/metrics")
    fun metrics(): Mono<Map<String, Long>> = Mono.fromCallable { metrics.collectMetrics() }.subscribeOn(Schedulers.boundedElastic())

    @GetMapping("/internal/users/{id}")
    fun findActive(@PathVariable id: UUID): Mono<ResponseEntity<UserView>> = Mono.fromCallable {
        users.findActive(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
    }.subscribeOn(Schedulers.boundedElastic())
}
