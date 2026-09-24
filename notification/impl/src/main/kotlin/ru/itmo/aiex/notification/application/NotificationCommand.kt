package ru.itmo.aiex.notification.application

import ru.itmo.aiex.notification.domain.NotificationType
import java.util.UUID

data class NotificationCommand(val recipientId: UUID, val type: NotificationType, val payload: Map<String, Any?>)
