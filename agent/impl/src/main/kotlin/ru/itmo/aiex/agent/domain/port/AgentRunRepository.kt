package ru.itmo.aiex.agent.domain.port

import ru.itmo.aiex.agent.domain.AgentRun
import ru.itmo.aiex.agent.domain.AgentRunStatus
import java.util.UUID

interface AgentRunRepository {
    fun insert(run: AgentRun): AgentRun

    fun findById(id: UUID): AgentRun?

    fun count(): Long

    fun countByStatus(status: AgentRunStatus): Long

    fun sumTokensIn(): Long

    fun sumTokensOut(): Long

    fun averageLatencyMs(): Double?
}
