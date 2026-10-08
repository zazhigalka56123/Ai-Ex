package ru.itmo.aiex.notification.dto

import ru.itmo.aiex.notification.entity.NotificationType
import java.util.UUID

data class NotificationCommand(val recipientId: UUID, val type: NotificationType, val payload: Map<String, Any?>, val eventId: UUID)
