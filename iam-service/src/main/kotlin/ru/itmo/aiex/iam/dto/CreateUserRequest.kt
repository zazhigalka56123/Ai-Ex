package ru.itmo.aiex.iam.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import ru.itmo.aiex.common.security.RoleCode
data class CreateUserRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 254)
    @field:Schema(example = "masha@example.com")
    val email: String,
    @field:NotBlank
    @field:Size(min = 1, max = 64)
    @field:Schema(example = "Маша")
    val displayName: String,
    @field:Size(max = 3)
    @field:Schema(description = "Роли; по умолчанию USER. В лаб. 3 назначать роли сможет только администратор")
    val roles: Set<RoleCode> = emptySet(),
)
