package ru.itmo.aiex.dialog.api

import java.time.Instant
import java.util.UUID

data class MessageView(
    val id: UUID,
    val conversationId: UUID,
    val personaId: UUID,
    val ownerId: UUID,
    val sender: SenderKind,
    val body: String,
    val createdAt: Instant,
    val flagged: Boolean,
)
