package ru.itmo.aiex.agent.dto

import java.util.UUID

data class GenerateReplyCommand(val conversationId: UUID, val personaId: UUID, val history: List<HistoryMessage>)
