package ru.itmo.aiex.dialog.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import ru.itmo.aiex.dialog.domain.Conversation
import ru.itmo.aiex.dialog.domain.ConversationStatus
import java.time.Instant
import java.util.UUID

data class ConversationResponse(
    val id: UUID,
    val personaId: UUID,
    val title: String,
    val status: ConversationStatus,
    @field:Schema(description = "Сколько сообщений в беседе (обе стороны)")
    val messageCount: Int,
    val createdAt: Instant,
    @field:Schema(description = "Время последнего сообщения; null - сообщений ещё не было")
    val lastMessageAt: Instant?,
)

fun Conversation.toResponse() = ConversationResponse(
    id = id,
    personaId = personaId,
    title = title,
    status = status,
    messageCount = messageCount,
    createdAt = createdAt,
    lastMessageAt = lastMessageAt,
)
