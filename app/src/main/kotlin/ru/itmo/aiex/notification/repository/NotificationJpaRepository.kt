package ru.itmo.aiex.notification.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import ru.itmo.aiex.notification.entity.Notification
import ru.itmo.aiex.notification.entity.NotificationStatus
import java.util.UUID

internal interface NotificationJpaRepository : JpaRepository<Notification, UUID> {
    fun findAllByRecipientId(recipientId: UUID, pageable: Pageable): Page<Notification>

    fun findAllByRecipientIdAndStatus(recipientId: UUID, status: NotificationStatus, pageable: Pageable): Page<Notification>

    fun countByStatus(status: NotificationStatus): Long
}
