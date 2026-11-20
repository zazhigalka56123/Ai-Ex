package ru.itmo.aiex.notification.repository

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.itmo.aiex.notification.entity.Notification
import java.util.UUID

interface NotificationRepository : ReactiveCrudRepository<Notification, UUID> {
    @Query(
        """
        SELECT * FROM notifications
        WHERE recipient_id = :recipient
        ORDER BY created_at DESC, id DESC LIMIT :size OFFSET :offset
        """,
    )
    fun findPageDescending(recipient: UUID, size: Int, offset: Long): Flux<Notification>

    @Query(
        """
        SELECT * FROM notifications
        WHERE recipient_id = :recipient
        ORDER BY created_at ASC, id ASC LIMIT :size OFFSET :offset
        """,
    )
    fun findPageAscending(recipient: UUID, size: Int, offset: Long): Flux<Notification>

    @Query("SELECT count(*) FROM notifications WHERE recipient_id = :recipient")
    fun countByRecipient(recipient: UUID): Mono<Long>
}
