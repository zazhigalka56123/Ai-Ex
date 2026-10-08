package ru.itmo.aiex.iam.controller

import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import ru.itmo.aiex.iam.dto.UserView
import ru.itmo.aiex.iam.service.UserQuery
import java.util.UUID

@RestController
@Profile("microservice")
class AccountInternalController(private val users: UserQuery) {
    @GetMapping("/internal/users/{userId}")
    fun findActiveUser(@PathVariable userId: UUID): Mono<ResponseEntity<UserView>> =
        Mono.fromCallable { users.findActive(userId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build() }
            .subscribeOn(Schedulers.boundedElastic())
}
