package ru.itmo.aiex.agent.dto

import java.util.UUID

data class PersonaDescription(val agentRunId: UUID, val text: String, val model: String)
