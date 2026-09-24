package ru.itmo.aiex.agent.api

import java.util.UUID

data class PersonaDescription(val agentRunId: UUID, val text: String, val model: String)
