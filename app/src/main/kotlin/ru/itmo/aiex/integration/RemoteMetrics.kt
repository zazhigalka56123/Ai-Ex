package ru.itmo.aiex.integration

import org.springframework.stereotype.Component
import ru.itmo.aiex.common.metrics.MetricsContributor

@Component
class RemoteMetrics(private val users: IdentityClient, private val notifications: NotificationClient) : MetricsContributor {
    override fun collectMetrics(): Map<String, Long> = users.metrics() + notifications.metrics()
}
