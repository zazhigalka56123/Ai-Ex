package ru.itmo.aiex.notification.dto

import ru.itmo.aiex.notification.entity.Notification
import ru.itmo.aiex.notification.entity.NotificationType
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

data class NotificationResponse(val id: UUID, val type: NotificationType, val payload: JsonNode, val createdAt: Instant)

fun Notification.toResponse(json: JsonMapper) = NotificationResponse(
    id,
    type,
    json.readTree(payload),
    createdAt,
)
