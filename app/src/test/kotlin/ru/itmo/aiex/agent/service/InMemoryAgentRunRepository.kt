package ru.itmo.aiex.agent.service

import ru.itmo.aiex.agent.entity.AgentRun
import ru.itmo.aiex.agent.entity.AgentRunStatus
import ru.itmo.aiex.agent.repository.AgentRunRepository
import java.util.UUID

internal class InMemoryAgentRunRepository : AgentRunRepository {
    private val runs = linkedMapOf<UUID, AgentRun>()

    fun all(): List<AgentRun> = runs.values.toList()

    override fun insert(run: AgentRun): AgentRun = run.also { runs[it.id] = it }

    override fun findById(id: UUID): AgentRun? = runs[id]

    override fun count(): Long = runs.size.toLong()

    override fun countByStatus(status: AgentRunStatus): Long = runs.values.count { it.status == status }.toLong()

    override fun sumTokensIn(): Long = runs.values.sumOf { (it.tokensIn ?: 0).toLong() }

    override fun sumTokensOut(): Long = runs.values.sumOf { (it.tokensOut ?: 0).toLong() }

    override fun averageLatencyMs(): Double? =
        runs.values.filter { it.model != SafetyReplies.GUARDRAIL_MODEL }.mapNotNull { it.latencyMs }.takeIf { it.isNotEmpty() }?.average()
}
