package ru.itmo.aiex.notification.service

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.metrics.MetricsContributor
import ru.itmo.aiex.notification.entity.NotificationStatus
import ru.itmo.aiex.notification.repository.NotificationRepository
@Component
@Transactional(readOnly = true)
class NotificationMetrics(private val notifications: NotificationRepository) : MetricsContributor {
    override fun metrics(): Map<String, Long> = mapOf(
        "notifications.sent" to notifications.countByStatus(NotificationStatus.SENT),
        "notifications.failed" to notifications.countByStatus(NotificationStatus.FAILED),
        "notifications.pending" to notifications.countByStatus(NotificationStatus.PENDING),
    )
}
