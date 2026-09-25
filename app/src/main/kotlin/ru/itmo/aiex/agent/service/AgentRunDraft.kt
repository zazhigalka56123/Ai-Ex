package ru.itmo.aiex.agent.service

import ru.itmo.aiex.agent.entity.AgentRunKind

import java.util.UUID

data class AgentRunDraft(val kind: AgentRunKind, val conversationId: UUID?, val personaId: UUID, val personaProfileId: UUID?, val prompt: Prompt) {
    init {
        require((kind == AgentRunKind.REPLY) == (conversationId != null)) { "Прогон $kind и conversationId=$conversationId несовместимы" }
    }
}
