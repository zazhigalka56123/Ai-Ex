package ru.itmo.aiex.iam.service

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.iam.dto.UserView
import ru.itmo.aiex.iam.entity.User
import ru.itmo.aiex.iam.repository.UserRepository
import java.util.UUID

@Component
@Transactional(readOnly = true)
class UserQueryAdapter(private val users: UserRepository) : UserQuery {
    override fun findActive(userId: UUID): UserView? = users.findById(userId)?.takeIf { it.isActive }?.toView()

    private fun User.toView() = UserView(id = id, displayName = displayName, roles = roleCodes, active = isActive)
}
