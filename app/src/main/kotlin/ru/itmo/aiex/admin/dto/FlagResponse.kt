package ru.itmo.aiex.admin.dto

import io.swagger.v3.oas.annotations.media.Schema
import ru.itmo.aiex.admin.service.FlagView
import ru.itmo.aiex.admin.entity.FlagSource
import ru.itmo.aiex.admin.entity.FlagStatus
import ru.itmo.aiex.admin.entity.ModerationFlag
import ru.itmo.aiex.common.moderation.FlagReason
import ru.itmo.aiex.dialog.dto.MessageView
import java.time.Instant
import java.util.UUID

data class FlagResponse(
    val id: UUID,
    val messageId: UUID,
    val conversationId: UUID,
    val personaId: UUID,
    @field:Schema(description = "Автор жалобы; null - флаг поставили guardrails")
    val reporterId: UUID?,
    val source: FlagSource,
    val reason: FlagReason,
    val comment: String?,
    val status: FlagStatus,
    val resolution: String?,
    @field:Schema(description = "Администратор, разбирающий флаг")
    val assigneeId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val resolvedAt: Instant?,
    @field:Schema(description = "Превью сообщения для администратора; null, если сообщения уже нет или ответ - автору жалобы")
    val message: MessagePreviewResponse?,
    @field:Schema(description = "Результат архивации персоны - только в ответе на вердикт с archivePersona = true")
    val personaArchived: Boolean?,
)

fun ModerationFlag.toResponse(message: MessageView? = null, personaArchived: Boolean? = null) = FlagResponse(
    id = id,
    messageId = messageId,
    conversationId = conversationId,
    personaId = personaId,
    reporterId = reporterId,
    source = source,
    reason = reason,
    comment = comment,
    status = status,
    resolution = resolution,
    assigneeId = assigneeId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    resolvedAt = resolvedAt,
    message = message?.let { MessagePreviewResponse(it.sender, it.body, it.createdAt) },
    personaArchived = personaArchived,
)

fun FlagView.toResponse() = flag.toResponse(message, personaArchived)
