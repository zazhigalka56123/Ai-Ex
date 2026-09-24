package ru.itmo.aiex.admin.domain

import java.util.UUID

data class FlaggedMessage(val messageId: UUID, val conversationId: UUID, val personaId: UUID)
