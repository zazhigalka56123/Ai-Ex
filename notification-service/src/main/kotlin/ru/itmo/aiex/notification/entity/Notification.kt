package ru.itmo.aiex.notification.entity

import java.time.Instant
import java.util.UUID

data class Notification(
    val id: UUID,
    val recipientId: UUID,
    val type: NotificationType,
    val payload: String,
    val createdAt: Instant,
    val status: NotificationStatus = NotificationStatus.PENDING,
    val attempts: Int = 0,
    val sentAt: Instant? = null,
    val eventId: UUID = id,
)
