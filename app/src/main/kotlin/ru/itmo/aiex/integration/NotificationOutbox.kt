package ru.itmo.aiex.integration

import org.slf4j.LoggerFactory
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import ru.itmo.aiex.notification.service.NotificationCommand
import ru.itmo.aiex.notification.service.NotificationPort
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@FeignClient(name = "notification-service")
interface NotificationClient {
    @GetMapping("/internal/metrics")
    fun metrics(): Map<String, Long>

    @PutMapping("/internal/notifications/{id}")
    fun deliver(@PathVariable id: UUID, @RequestBody command: NotificationCommand): UUID
}

@Component
class NotificationOutbox(private val jdbc: JdbcTemplate, private val json: JsonMapper) : NotificationPort {
    @Transactional
    override fun sendNotification(command: NotificationCommand): UUID {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO notification_outbox (id, payload) VALUES (?, ?::jsonb)", id, json.writeValueAsString(command))
        return id
    }
}

@Component
class NotificationOutboxDispatcher(private val jdbc: JdbcTemplate, private val client: NotificationClient, private val json: JsonMapper) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${ai-ex.outbox.delay-ms:2000}", initialDelayString = "\${ai-ex.outbox.delay-ms:2000}")
    @Transactional
    fun dispatch() {
        val rows = jdbc.queryForList(
            "SELECT id, payload::text FROM notification_outbox WHERE next_attempt_at <= now() ORDER BY created_at LIMIT 25 FOR UPDATE SKIP LOCKED",
        )
        for (row in rows) {
            val id = row["id"] as UUID
            try {
                client.deliver(id, json.readValue(row["payload"] as String, NotificationCommand::class.java))
                jdbc.update("DELETE FROM notification_outbox WHERE id = ?", id)
            } catch (ex: Exception) {
                jdbc.update(
                    "UPDATE notification_outbox SET attempts = attempts + 1, next_attempt_at = now() + interval '10 seconds' WHERE id = ?",
                    id,
                )
                log.warn("Notification {} remains queued: {}", id, ex.javaClass.simpleName)
            }
        }
    }
}
