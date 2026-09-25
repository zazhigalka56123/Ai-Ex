package ru.itmo.aiex.agent.dto

import java.util.UUID

data class GeneratedReply(
    val agentRunId: UUID,
    val text: String,
    val model: String,
    val latencyMs: Int,
    val tokensIn: Int,
    val tokensOut: Int,
    val guardrailHits: List<GuardrailHit> = emptyList(),
)
