package ru.itmo.aiex.iam.api

import java.util.UUID

interface UserQuery {
    fun findActive(userId: UUID): UserView?
}
