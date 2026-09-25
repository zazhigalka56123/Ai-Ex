package ru.itmo.aiex.iam.service

import ru.itmo.aiex.iam.dto.UserView

import java.util.UUID

interface UserQuery {
    fun findActive(userId: UUID): UserView?
}
