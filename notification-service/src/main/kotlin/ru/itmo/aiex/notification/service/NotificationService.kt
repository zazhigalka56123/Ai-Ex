package ru.itmo.aiex.notification.service

import org.slf4j.LoggerFactory
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@Service
class NotificationService(private val database: DatabaseClient, private val json: JsonMapper) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun deliver(id: UUID, command: NotificationCommand): Mono<UUID> = database.sql(
        """
        INSERT INTO notifications (id, recipient_id, type, payload, status, attempts, created_at, sent_at)
        VALUES (:id, :recipient, :type, :payload, 'SENT', 1, now(), now())
        ON CONFLICT (id) DO NOTHING
        """.trimIndent(),
    ).bind("id", id)
        .bind("recipient", command.recipientId)
        .bind("type", command.type.name)
        .bind("payload", json.writeValueAsString(command.payload))
        .fetch().rowsUpdated()
        .doOnNext { inserted -> if (inserted > 0) log.info("Notification {} delivered to {}", id, command.recipientId) }
        .thenReturn(id)
}
