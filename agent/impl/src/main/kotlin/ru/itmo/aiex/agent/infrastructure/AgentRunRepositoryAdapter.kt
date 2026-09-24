package ru.itmo.aiex.agent.infrastructure

import jakarta.persistence.EntityManager
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import ru.itmo.aiex.agent.domain.AgentRun
import ru.itmo.aiex.agent.domain.AgentRunStatus
import ru.itmo.aiex.agent.domain.SafetyReplies
import ru.itmo.aiex.agent.domain.port.AgentRunRepository
import java.util.UUID

@Repository
internal class AgentRunRepositoryAdapter(private val jpa: AgentRunJpaRepository, private val entityManager: EntityManager) : AgentRunRepository {
    override fun insert(run: AgentRun): AgentRun {
        entityManager.persist(run)
        return run
    }

    override fun findById(id: UUID): AgentRun? = jpa.findByIdOrNull(id)

    override fun count(): Long = jpa.count()

    override fun countByStatus(status: AgentRunStatus): Long = jpa.countByStatus(status)

    override fun sumTokensIn(): Long = jpa.sumTokensIn()

    override fun sumTokensOut(): Long = jpa.sumTokensOut()

    override fun averageLatencyMs(): Double? = jpa.averageLatencyMs(SafetyReplies.GUARDRAIL_MODEL)
}
