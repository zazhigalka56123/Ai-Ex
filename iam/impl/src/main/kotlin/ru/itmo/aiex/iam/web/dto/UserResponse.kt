package ru.itmo.aiex.iam.web.dto

import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.domain.User
import ru.itmo.aiex.iam.domain.UserStatus
import java.time.Instant
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val email: String,
    val displayName: String,
    val status: UserStatus,
    val roles: Set<RoleCode>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun User.toResponse() = UserResponse(
    id = id,
    email = email,
    displayName = displayName,
    status = status,
    roles = roleCodes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
