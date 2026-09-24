package ru.itmo.aiex.iam.domain.port

import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.iam.domain.User
import ru.itmo.aiex.iam.domain.UserStatus
import java.util.UUID

interface UserRepository {
    fun saveAndFlush(user: User): User

    fun findById(id: UUID): User?

    fun existsByEmail(email: String): Boolean

    fun findPage(status: UserStatus?, page: PageQuery): PageView<User>

    fun countByStatus(status: UserStatus): Long
}
