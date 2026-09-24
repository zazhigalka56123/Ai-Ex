package ru.itmo.aiex.agent.application

import ru.itmo.aiex.agent.domain.AgentRunKind
import ru.itmo.aiex.agent.domain.Prompt
import java.util.UUID

data class AgentRunDraft(val kind: AgentRunKind, val conversationId: UUID?, val personaId: UUID, val personaProfileId: UUID?, val prompt: Prompt) {
    init {
        require((kind == AgentRunKind.REPLY) == (conversationId != null)) { "Прогон $kind и conversationId=$conversationId несовместимы" }
    }
}
