package ru.itmo.aiex.dialog.dto

import io.swagger.v3.oas.annotations.media.Schema
import ru.itmo.aiex.dialog.entity.Message
import ru.itmo.aiex.dialog.entity.MessageSender
import java.time.Instant
import java.util.UUID

data class MessageResponse(
    val id: UUID,
    val conversationId: UUID,
    val sender: MessageSender,
    val text: String,
    val createdAt: Instant,
    @field:Schema(description = "Сообщение помечено для модерации (жалоба или guardrails)")
    val flagged: Boolean,
)

fun Message.toResponse() = MessageResponse(
    id = id,
    conversationId = conversationId,
    sender = sender,
    text = body,
    createdAt = createdAt,
    flagged = flagged,
)
