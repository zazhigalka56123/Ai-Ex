package ru.itmo.aiex.notification.application

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.metrics.MetricsContributor
import ru.itmo.aiex.notification.domain.NotificationStatus
import ru.itmo.aiex.notification.domain.port.NotificationRepository

@Component
@Transactional(readOnly = true)
class NotificationMetrics(private val notifications: NotificationRepository) : MetricsContributor {
    override fun metrics(): Map<String, Long> = mapOf(
        "notifications.sent" to notifications.countByStatus(NotificationStatus.SENT),
        "notifications.failed" to notifications.countByStatus(NotificationStatus.FAILED),
        "notifications.pending" to notifications.countByStatus(NotificationStatus.PENDING),
    )
}
