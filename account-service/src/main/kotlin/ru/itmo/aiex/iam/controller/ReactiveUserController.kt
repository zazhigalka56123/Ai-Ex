package ru.itmo.aiex.iam.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.web.AiExHeaders
import ru.itmo.aiex.common.web.ApiPaths
import ru.itmo.aiex.common.web.PageParams
import ru.itmo.aiex.iam.dto.CreateUserRequest
import ru.itmo.aiex.iam.dto.UpdateUserRequest
import ru.itmo.aiex.iam.dto.UserResponse
import ru.itmo.aiex.iam.entity.UserStatus
import ru.itmo.aiex.iam.service.ReactiveUserService
import ru.itmo.aiex.iam.service.RegisterUserCommand
import ru.itmo.aiex.iam.service.UpdateUserCommand
import ru.itmo.aiex.reactive.ReactiveResponses
import java.net.URI
import java.util.UUID

@RestController
@Profile("microservice")
@RequestMapping("${ApiPaths.V1}/users")
@Tag(name = "Пользователи", description = "Регистрация, роли и блокировка пользователей")
class ReactiveUserController(private val users: ReactiveUserService) {
    @PostMapping
    @Operation(operationId = "createUser", summary = "Создать пользователя", description = "В лаб. 1-2 регистрация открыта.")
    @ApiResponse(responseCode = "201", description = "Пользователь создан", headers = [Header(name = "Location", description = "URI пользователя")])
    @ApiResponse(responseCode = "400", description = "Тело запроса не прошло валидацию")
    @ApiResponse(responseCode = "409", description = "Email уже занят")
    fun createUser(@Valid @RequestBody request: CreateUserRequest): Mono<ResponseEntity<UserResponse>> =
        users.registerUser(RegisterUserCommand(request.email, request.displayName, request.roles))
            .map { ResponseEntity.created(URI.create("${ApiPaths.V1}/users/${it.id}")).body(it) }

    @GetMapping
    @Operation(
        operationId = "listUsers",
        summary = "Список пользователей",
        description = "Только администратор. Offset-пагинация, общее количество - в `X-Total-Count`.",
        parameters = [
            Parameter(name = AiExHeaders.USER_ID, `in` = ParameterIn.HEADER, required = true),
            Parameter(name = "page", `in` = ParameterIn.QUERY, description = "Номер страницы с 0"),
            Parameter(name = "size", `in` = ParameterIn.QUERY, description = "Размер страницы, не больше 50"),
            Parameter(name = "sort", `in` = ParameterIn.QUERY, description = "createdAt|email|displayName,asc|desc"),
        ],
    )
    @ApiResponse(responseCode = "200", description = "Страница пользователей")
    @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    fun getUsers(
        @Parameter(hidden = true) actor: Actor,
        @Parameter(hidden = true)
        @PageParams(sortable = ["createdAt", "email", "displayName"], defaultSort = "createdAt,desc")
        page: PageQuery,
        @RequestParam(required = false) status: UserStatus?,
        request: ServerHttpRequest,
    ): Mono<ResponseEntity<List<UserResponse>>> = users.getUsers(actor, status, page).map { ReactiveResponses.page(it, request) }

    @GetMapping("/{id}")
    @Operation(
        operationId = "getUser",
        summary = "Пользователь по id",
        description = "Администратор - любой, остальные - только себя.",
        parameters = [Parameter(name = AiExHeaders.USER_ID, `in` = ParameterIn.HEADER, required = true)],
    )
    @ApiResponse(responseCode = "200", description = "Пользователь")
    @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    fun getUser(@Parameter(hidden = true) actor: Actor, @PathVariable id: UUID): Mono<UserResponse> = users.getUser(actor, id)

    @PatchMapping("/{id}")
    @Operation(
        operationId = "updateUser",
        summary = "Изменить пользователя",
        description = "Имя меняет сам пользователь или администратор; статус (блокировка) и роли - только администратор.",
        parameters = [Parameter(name = AiExHeaders.USER_ID, `in` = ParameterIn.HEADER, required = true)],
    )
    @ApiResponse(responseCode = "200", description = "Пользователь изменён")
    @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    @ApiResponse(responseCode = "409", description = "Недопустимый переход статуса или параллельное изменение")
    fun updateUser(
        @Parameter(hidden = true) actor: Actor,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateUserRequest,
    ): Mono<UserResponse> = users.updateUser(actor, id, UpdateUserCommand(request.displayName, request.status, request.roles))
}
