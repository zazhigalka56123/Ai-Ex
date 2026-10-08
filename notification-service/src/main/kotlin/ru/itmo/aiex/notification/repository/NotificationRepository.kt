package ru.itmo.aiex.notification.repository

import io.r2dbc.spi.Row
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.paging.SortDirection
import ru.itmo.aiex.notification.entity.Notification
import ru.itmo.aiex.notification.entity.NotificationStatus
import ru.itmo.aiex.notification.entity.NotificationType
import java.time.Instant
import java.util.UUID

@Repository
class NotificationRepository(private val database: DatabaseClient) {
    fun insert(notification: Notification): Mono<Boolean> = database
        .sql(
            """
            INSERT INTO notifications (id, event_id, recipient_id, type, payload, created_at, status, attempts)
            VALUES (:id, :eventId, :recipientId, :type, :payload, :createdAt, :status, :attempts)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        )
        .bind("id", notification.id)
        .bind("eventId", notification.eventId)
        .bind("recipientId", notification.recipientId)
        .bind("type", notification.type.name)
        .bind("payload", notification.payload)
        .bind("createdAt", notification.createdAt)
        .bind("status", notification.status.name)
        .bind("attempts", notification.attempts)
        .fetch()
        .rowsUpdated()
        .map { it > 0 }

    fun findByEventId(eventId: UUID): Mono<Notification> = database
        .sql("SELECT * FROM notifications WHERE event_id = :eventId")
        .bind("eventId", eventId)
        .map { row, _ -> row.toNotification() }
        .one()

    fun metrics(): Mono<Map<String, Long>> = database
        .sql("SELECT status, COUNT(*) AS total FROM notifications GROUP BY status")
        .map { row, _ -> row.get("status", String::class.java)!!.lowercase() to row.get("total", Long::class.javaObjectType)!! }
        .all()
        .collectMap({ "notifications.${it.first}" }, { it.second })
        .map { mapOf("notifications.sent" to 0L, "notifications.failed" to 0L, "notifications.pending" to 0L) + it }

    fun recordDelivery(id: UUID, status: NotificationStatus, sentAt: Instant?): Mono<Void> {
        val update = database
            .sql("UPDATE notifications SET status = :status, attempts = attempts + 1, sent_at = :sentAt WHERE id = :id")
            .bind("id", id)
            .bind("status", status.name)
        val bound = if (sentAt == null) update.bindNull("sentAt", Instant::class.java) else update.bind("sentAt", sentAt)
        return bound.fetch().rowsUpdated().then()
    }

    fun findPage(recipientId: UUID, status: NotificationStatus?, page: PageQuery): Mono<PageView<Notification>> {
        val predicate = if (status == null) "recipient_id = :recipientId" else "recipient_id = :recipientId AND status = :status"
        val direction = if (page.sort.firstOrNull()?.direction == SortDirection.ASC) "ASC" else "DESC"
        val select = database
            .sql("SELECT * FROM notifications WHERE $predicate ORDER BY created_at $direction, id DESC LIMIT :size OFFSET :offset")
            .bind("recipientId", recipientId)
            .bind("size", page.size)
            .bind("offset", page.offset)
        val count = database.sql("SELECT COUNT(*) FROM notifications WHERE $predicate").bind("recipientId", recipientId)
        val items = (if (status == null) select else select.bind("status", status.name)).map { row, _ -> row.toNotification() }.all().collectList()
        val filteredCount = if (status == null) count else count.bind("status", status.name)
        val total = filteredCount.map { row, _ -> row.get(0, Long::class.javaObjectType)!! }.one()
        return Mono.zip(items, total).map { PageView(it.t1, page.page, page.size, it.t2) }
    }

    private fun Row.toNotification() = Notification(
        id = get("id", UUID::class.java)!!,
        recipientId = get("recipient_id", UUID::class.java)!!,
        type = NotificationType.valueOf(get("type", String::class.java)!!),
        payload = get("payload", String::class.java)!!,
        createdAt = get("created_at", Instant::class.java)!!,
        status = NotificationStatus.valueOf(get("status", String::class.java)!!),
        attempts = get("attempts", Int::class.javaObjectType)!!,
        sentAt = get("sent_at", Instant::class.java),
        eventId = get("event_id", UUID::class.java)!!,
    )
}
