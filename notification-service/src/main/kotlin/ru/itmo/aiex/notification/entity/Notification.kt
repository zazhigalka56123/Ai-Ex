package ru.itmo.aiex.notification.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("notifications")
data class Notification(
    @Id val id: UUID,
    val recipientId: UUID,
    val type: NotificationType,
    val payload: String,
    val status: NotificationStatus,
    val attempts: Int,
    val createdAt: Instant,
    val sentAt: Instant?,
)
