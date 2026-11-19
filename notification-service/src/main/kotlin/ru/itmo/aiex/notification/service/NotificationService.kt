package ru.itmo.aiex.notification.service

import org.slf4j.LoggerFactory
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.itmo.aiex.notification.entity.Notification
import ru.itmo.aiex.notification.repository.NotificationRepository
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@Service
class NotificationService(private val database: DatabaseClient, private val notifications: NotificationRepository, private val json: JsonMapper) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun deliver(id: UUID, command: NotificationCommand): Mono<UUID> = database.sql(
        """
        INSERT INTO notifications (id, recipient_id, type, payload, created_at)
        VALUES (:id, :recipient, :type, :payload, now())
        ON CONFLICT (id) DO NOTHING
        """.trimIndent(),
    ).bind("id", id)
        .bind("recipient", command.recipientId)
        .bind("type", command.type.name)
        .bind("payload", json.writeValueAsString(command.payload))
        .fetch().rowsUpdated()
        .doOnNext { inserted -> if (inserted > 0) log.info("Notification {} delivered to {}", id, command.recipientId) }
        .thenReturn(id)

    fun findNotifications(recipientId: UUID, size: Int, offset: Long, ascending: Boolean): Flux<Notification> = if (ascending) {
        notifications.findPageAscending(recipientId, size, offset)
    } else {
        notifications.findPageDescending(recipientId, size, offset)
    }

    fun countNotifications(recipientId: UUID): Mono<Long> = notifications.countByRecipient(recipientId)

    fun collectMetrics(): Mono<Map<String, Long>> = notifications.count().map { mapOf("notifications.sent" to it) }
}
