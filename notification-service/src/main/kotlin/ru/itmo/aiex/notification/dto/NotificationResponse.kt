package ru.itmo.aiex.notification.dto

import ru.itmo.aiex.notification.entity.Notification
import ru.itmo.aiex.notification.entity.NotificationStatus
import ru.itmo.aiex.notification.entity.NotificationType
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonTypeRef
import java.time.Instant
import java.util.UUID

data class NotificationResponse(
    val id: UUID,
    val type: NotificationType,
    val status: NotificationStatus,
    val payload: Map<String, Any?>,
    val attempts: Int,
    val createdAt: Instant,
    val sentAt: Instant?,
)

fun Notification.toResponse(mapper: JsonMapper) = NotificationResponse(
    id,
    type,
    status,
    mapper.readValue(payload, jacksonTypeRef<Map<String, Any?>>()),
    attempts,
    createdAt,
    sentAt,
)
