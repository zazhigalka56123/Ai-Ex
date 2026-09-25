package ru.itmo.aiex.notification.dto

import io.swagger.v3.oas.annotations.media.Schema
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
    @field:Schema(description = "Идентификаторы и статусы, связанные с событием (без текстов)", example = """{"personaId":"0192ac8f-…"}""")
    val payload: Map<String, Any?>,
    @field:Schema(description = "Сколько раз пытались доставить")
    val attempts: Int,
    val createdAt: Instant,
    val sentAt: Instant?,
)

fun Notification.toResponse(jsonMapper: JsonMapper) = NotificationResponse(
    id = id,
    type = type,
    status = status,
    payload = jsonMapper.readValue(payload, jacksonTypeRef<Map<String, Any?>>()),
    attempts = attempts,
    createdAt = createdAt,
    sentAt = sentAt,
)
