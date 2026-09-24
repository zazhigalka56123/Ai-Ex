package ru.itmo.aiex.iam.application

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.metrics.MetricsContributor
import ru.itmo.aiex.iam.domain.UserStatus
import ru.itmo.aiex.iam.domain.port.UserRepository

@Component
@Transactional(readOnly = true)
class IamMetrics(private val users: UserRepository) : MetricsContributor {
    override fun metrics(): Map<String, Long> = mapOf(
        "users.active" to users.countByStatus(UserStatus.ACTIVE),
        "users.blocked" to users.countByStatus(UserStatus.BLOCKED),
    )
}
