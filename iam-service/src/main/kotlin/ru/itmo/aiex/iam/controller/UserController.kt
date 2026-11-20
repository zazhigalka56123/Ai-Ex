package ru.itmo.aiex.iam.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
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
@RequestMapping("/api/v1/users")
@Tag(name = "Пользователи")
class UserController(private val users: UserService) {
    @PostMapping
    @Operation(operationId = "createUser", summary = "Создать пользователя")
    @ApiResponse(responseCode = "201", description = "Пользователь создан; Location содержит URI пользователя")
    @ApiResponse(responseCode = "400", description = "Некорректное тело запроса")
    @ApiResponse(responseCode = "409", description = "Email уже занят")
    fun createUser(actor: Actor, @Valid @RequestBody request: CreateUserRequest): Mono<ResponseEntity<UserResponse>> = blocking {
        val user = users.registerUser(actor, RegisterUserCommand(request.email, request.displayName, request.roles, request.password)).toResponse()
        ResponseEntity.created(URI.create("/api/v1/users/${user.id}")).body(user)
    }

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "listUsers",
        summary = "Список пользователей",
        description = "Только администратор. Общее количество — в X-Total-Count.",
    )
    fun getUsers(
        actor: Actor,
        @PageParams(sortable = ["createdAt", "email", "displayName"], defaultSort = "createdAt,desc") page: PageQuery,
        @RequestParam(required = false) status: UserStatus?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): Flux<UserResponse> {
        val requestUri = UriComponentsBuilder.fromUriString(request.requestURL.toString()).query(request.queryString)
        return blocking {
            val result = users.getUsers(actor, status, page).map { it.toResponse() }
            Responses.pageHeaders(result, requestUri).forEach { name, values ->
                values.forEach { response.addHeader(name, it) }
            }
            result.items
        }.flatMapMany { Flux.fromIterable(it) }
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getUser", summary = "Получить пользователя")
    fun getUser(actor: Actor, @PathVariable id: UUID): Mono<UserResponse> = blocking { users.getUser(actor, id).toResponse() }

    @PatchMapping("/{id}")
    @Operation(operationId = "updateUser", summary = "Изменить пользователя")
    fun updateUser(actor: Actor, @PathVariable id: UUID, @Valid @RequestBody request: UpdateUserRequest): Mono<UserResponse> = blocking {
        users.updateUser(actor, id, UpdateUserCommand(request.displayName, request.status, request.roles, request.password)).toResponse()
    }

    private fun <T : Any> blocking(action: () -> T): Mono<T> = Mono.fromCallable(action).subscribeOn(Schedulers.boundedElastic())
}
