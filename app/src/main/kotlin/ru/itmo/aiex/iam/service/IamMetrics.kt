package ru.itmo.aiex.iam.service

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.metrics.MetricsContributor
import ru.itmo.aiex.iam.entity.UserStatus
import ru.itmo.aiex.iam.repository.UserRepository
@Component
@Transactional(readOnly = true)
class IamMetrics(private val users: UserRepository) : MetricsContributor {
    override fun metrics(): Map<String, Long> = mapOf(
        "users.active" to users.countByStatus(UserStatus.ACTIVE),
        "users.blocked" to users.countByStatus(UserStatus.BLOCKED),
    )
}
