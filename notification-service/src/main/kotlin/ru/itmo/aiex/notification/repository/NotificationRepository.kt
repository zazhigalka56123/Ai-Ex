package ru.itmo.aiex.notification.repository

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.itmo.aiex.notification.entity.Notification
import java.util.UUID

interface NotificationRepository : ReactiveCrudRepository<Notification, UUID> {
    fun countByStatus(status: ru.itmo.aiex.notification.entity.NotificationStatus): Mono<Long>

    @Query(
        "SELECT * FROM notifications WHERE recipient_id = :recipient AND (:status IS NULL OR status = :status) ORDER BY created_at DESC, id DESC LIMIT :size OFFSET :offset",
    )
    fun page(recipient: UUID, status: String?, size: Int, offset: Long): Flux<Notification>

    @Query(
        "SELECT * FROM notifications WHERE recipient_id = :recipient AND (:status IS NULL OR status = :status) ORDER BY created_at ASC, id ASC LIMIT :size OFFSET :offset",
    )
    fun pageAscending(recipient: UUID, status: String?, size: Int, offset: Long): Flux<Notification>

    @Query("SELECT count(*) FROM notifications WHERE recipient_id = :recipient AND (:status IS NULL OR status = :status)")
    fun count(recipient: UUID, status: String?): Mono<Long>
}
