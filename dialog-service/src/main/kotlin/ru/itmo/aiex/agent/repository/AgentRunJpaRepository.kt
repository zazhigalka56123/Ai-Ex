package ru.itmo.aiex.agent.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.itmo.aiex.agent.entity.AgentRun
import ru.itmo.aiex.agent.entity.AgentRunStatus
import java.util.UUID

internal interface AgentRunJpaRepository : JpaRepository<AgentRun, UUID> {
    fun countByStatus(status: AgentRunStatus): Long

    @Query("select coalesce(sum(r.tokensIn), 0L) from AgentRun r")
    fun sumTokensIn(): Long

    @Query("select coalesce(sum(r.tokensOut), 0L) from AgentRun r")
    fun sumTokensOut(): Long

    @Query("select avg(r.latencyMs) from AgentRun r where r.latencyMs is not null and r.model <> :excludedModel")
    fun averageLatencyMs(@Param("excludedModel") excludedModel: String): Double?
}
