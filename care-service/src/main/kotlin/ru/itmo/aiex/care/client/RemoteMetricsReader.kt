package ru.itmo.aiex.care.client

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import ru.itmo.aiex.remote.AccountClient
import ru.itmo.aiex.remote.DialogClient
import ru.itmo.aiex.remote.NotificationClient
import ru.itmo.aiex.remote.PersonaClient

@Component
@Profile("microservice")
class RemoteMetricsReader(
    private val accounts: AccountClient,
    private val personas: PersonaClient,
    private val dialogs: DialogClient,
    private val notifications: NotificationClient,
) {
    fun getMetrics(): Map<String, Long> = accounts.metrics() + personas.metrics() + dialogs.metrics() + notifications.metrics()
}
