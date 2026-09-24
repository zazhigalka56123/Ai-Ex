package ru.itmo.aiex.agent.api

import java.util.UUID

data class GenerateReplyCommand(val conversationId: UUID, val personaId: UUID, val history: List<HistoryMessage>)
