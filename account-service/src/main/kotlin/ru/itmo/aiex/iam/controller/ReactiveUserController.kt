package ru.itmo.aiex.iam.controller

import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.web.PageParams
import ru.itmo.aiex.common.web.Responses
import ru.itmo.aiex.iam.dto.CreateUserRequest
import ru.itmo.aiex.iam.dto.UpdateUserRequest
import ru.itmo.aiex.iam.dto.UserResponse
import ru.itmo.aiex.iam.dto.toResponse
import ru.itmo.aiex.iam.entity.UserStatus
import ru.itmo.aiex.iam.service.RegisterUserCommand
import ru.itmo.aiex.iam.service.UpdateUserCommand
import ru.itmo.aiex.iam.service.UserService
import java.net.URI
import java.util.UUID

@RestController
@Profile("microservice")
@RequestMapping("/api/v1/users")
class ReactiveUserController(private val users: UserService) {
    @PostMapping
    fun createUser(@Valid @RequestBody request: CreateUserRequest): Mono<ResponseEntity<UserResponse>> = query {
        val user = users.register(RegisterUserCommand(request.email, request.displayName, request.roles))
        ResponseEntity.created(URI.create("/api/v1/users/${user.id}")).body(user.toResponse())
    }

    @GetMapping
    fun getUsers(
        actor: Actor,
        @PageParams(sortable = ["createdAt", "email", "displayName"], defaultSort = "createdAt,desc") page: PageQuery,
        @RequestParam(required = false) status: UserStatus?,
    ): Mono<ResponseEntity<List<UserResponse>>> = query {
        Responses.page(users.getUsers(actor, status, page).map { it.toResponse() })
    }

    @GetMapping("/{id}")
    fun getUser(actor: Actor, @PathVariable id: UUID): Mono<UserResponse> = query { users.get(actor, id).toResponse() }

    @PatchMapping("/{id}")
    fun updateUser(actor: Actor, @PathVariable id: UUID, @Valid @RequestBody request: UpdateUserRequest): Mono<UserResponse> = query {
        users.update(actor, id, UpdateUserCommand(request.displayName, request.status, request.roles)).toResponse()
    }

    private fun <T : Any> query(action: () -> T): Mono<T> = Mono.fromCallable(action).subscribeOn(Schedulers.boundedElastic())
}
