package ru.itmo.aiex.iam.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.entity.UserStatus
data class UpdateUserRequest(
    @field:Size(min = 1, max = 64)
    val displayName: String? = null,
    @field:Schema(description = "Только администратор")
    val status: UserStatus? = null,
    @field:Size(min = 1, max = 3)
    @field:Schema(description = "Только администратор")
    val roles: Set<RoleCode>? = null,
)
