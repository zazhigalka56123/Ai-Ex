package ru.itmo.aiex.iam.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.iam.service.RegisterUserCommand
import ru.itmo.aiex.iam.service.UpdateUserCommand
import ru.itmo.aiex.iam.service.UserService
import ru.itmo.aiex.iam.entity.UserStatus
import ru.itmo.aiex.iam.dto.CreateUserRequest
import ru.itmo.aiex.iam.dto.UpdateUserRequest
import ru.itmo.aiex.iam.dto.UserResponse
import ru.itmo.aiex.iam.dto.toResponse
import ru.itmo.aiex.common.web.ApiPaths
import ru.itmo.aiex.common.web.PageParams
import ru.itmo.aiex.common.web.Responses
import ru.itmo.aiex.common.web.openapi.ApiErrors
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.V1}/users")
@Tag(name = "Пользователи", description = "Регистрация, роли и блокировка пользователей")
class UserController(private val users: UserService) {
    @PostMapping
    @Operation(
        operationId = "createUser",
        summary = "Создать пользователя",
        description = "В лаб. 1 регистрация открыта. В лаб. 3 создавать пользователей сможет только администратор.",
    )
    @ApiResponse(responseCode = "201", description = "Пользователь создан", headers = [Header(name = "Location", description = "URI пользователя")])
    @ApiErrors(ErrorCode.VALIDATION_FAILED, ErrorCode.EMAIL_TAKEN)
    fun create(@Valid @RequestBody request: CreateUserRequest): ResponseEntity<UserResponse> {
        val user = users.register(RegisterUserCommand(request.email, request.displayName, request.roles))
        return Responses.created(user.toResponse(), "${ApiPaths.V1}/users/{id}", user.id)
    }

    @GetMapping
    @Operation(
        operationId = "listUsers",
        summary = "Список пользователей",
        description = "Только администратор. Offset-пагинация, общее количество - в `X-Total-Count`.",
    )
    @ApiResponse(responseCode = "200", description = "Страница пользователей")
    @ApiErrors(ErrorCode.FORBIDDEN)
    fun list(
        actor: Actor,
        @PageParams(sortable = ["createdAt", "email", "displayName"], defaultSort = "createdAt,desc") page: PageQuery,
        @RequestParam(required = false) status: UserStatus?,
    ): ResponseEntity<List<UserResponse>> = Responses.page(users.list(actor, status, page).map { it.toResponse() })

    @GetMapping("/{id}")
    @Operation(operationId = "getUser", summary = "Пользователь по id", description = "Администратор - любой, остальные - только себя.")
    @ApiResponse(responseCode = "200", description = "Пользователь")
    @ApiErrors(ErrorCode.USER_NOT_FOUND)
    fun get(actor: Actor, @PathVariable id: UUID): UserResponse = users.get(actor, id).toResponse()

    @PatchMapping("/{id}")
    @Operation(
        operationId = "updateUser",
        summary = "Изменить пользователя",
        description = "Имя меняет сам пользователь или администратор; статус (блокировка) и роли - только администратор.",
    )
    @ApiResponse(responseCode = "200", description = "Пользователь изменён")
    @ApiErrors(
        ErrorCode.VALIDATION_FAILED,
        ErrorCode.FORBIDDEN,
        ErrorCode.USER_NOT_FOUND,
        ErrorCode.USER_INVALID_STATE,
        ErrorCode.CONCURRENT_MODIFICATION,
    )
    fun update(actor: Actor, @PathVariable id: UUID, @Valid @RequestBody request: UpdateUserRequest): UserResponse =
        users.update(actor, id, UpdateUserCommand(request.displayName, request.status, request.roles)).toResponse()
}
