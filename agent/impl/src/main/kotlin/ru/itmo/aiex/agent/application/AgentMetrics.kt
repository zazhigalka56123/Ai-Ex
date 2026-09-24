package ru.itmo.aiex.agent.application

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.agent.domain.AgentRunStatus
import ru.itmo.aiex.agent.domain.port.AgentRunRepository
import ru.itmo.aiex.common.metrics.MetricsContributor
import kotlin.math.roundToLong

@Component
@Transactional(readOnly = true)
class AgentMetrics(private val runs: AgentRunRepository) : MetricsContributor {
    override fun metrics(): Map<String, Long> = mapOf(
        "agent.runs.total" to runs.count(),
        "agent.runs.success" to runs.countByStatus(AgentRunStatus.SUCCESS),
        "agent.runs.failed" to runs.countByStatus(AgentRunStatus.FAILED),
        "agent.runs.timeout" to runs.countByStatus(AgentRunStatus.TIMEOUT),
        "agent.tokens.in" to runs.sumTokensIn(),
        "agent.tokens.out" to runs.sumTokensOut(),
        "agent.latency.avg_ms" to (runs.averageLatencyMs()?.roundToLong() ?: 0L),
    )
}
