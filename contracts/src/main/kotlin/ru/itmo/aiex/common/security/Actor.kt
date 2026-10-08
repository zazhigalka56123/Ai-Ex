package ru.itmo.aiex.common.security

import ru.itmo.aiex.common.error.ForbiddenException
import java.util.UUID

data class Actor(val userId: UUID, val roles: Set<RoleCode>) {
    fun has(role: RoleCode): Boolean = role in roles

    val isAdmin: Boolean get() = has(RoleCode.ADMIN)

    val isSpecialist: Boolean get() = has(RoleCode.SPECIALIST)

    fun requireRole(role: RoleCode) {
        if (!has(role)) throw ForbiddenException("Операция доступна только роли $role")
    }

    fun requireAnyRole(vararg required: RoleCode) {
        if (required.none(::has)) throw ForbiddenException("Операция доступна только ролям ${required.joinToString()}")
    }
}
