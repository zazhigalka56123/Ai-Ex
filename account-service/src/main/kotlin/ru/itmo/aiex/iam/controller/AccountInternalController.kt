package ru.itmo.aiex.iam.controller

import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import ru.itmo.aiex.iam.dto.UserView
import ru.itmo.aiex.iam.service.ReactiveUserService
import java.util.UUID

@RestController
@Profile("microservice")
@RequestMapping("/internal")
class AccountInternalController(private val users: ReactiveUserService) {
    @GetMapping("/users/{userId}")
    fun findActiveUser(@PathVariable userId: UUID): Mono<ResponseEntity<UserView>> =
        users.findActive(userId).map { ResponseEntity.ok(it) }.defaultIfEmpty(ResponseEntity.noContent().build())

    @GetMapping("/metrics")
    fun getMetrics(): Mono<Map<String, Long>> = users.collectMetrics()
}
