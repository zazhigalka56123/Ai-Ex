package ru.itmo.aiex.notification.service

import ru.itmo.aiex.notification.entity.NotificationType
import java.util.UUID

data class NotificationCommand(val recipientId: UUID, val type: NotificationType, val payload: Map<String, Any?>)
