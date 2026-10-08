package ru.itmo.aiex.agent.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.agent.entity.AgentRun
import ru.itmo.aiex.agent.entity.AgentRunStatus
import ru.itmo.aiex.agent.repository.AgentRunRepository
import ru.itmo.aiex.common.id.Ids
import ru.itmo.aiex.common.time.nowMicros
import java.time.Clock
import java.util.UUID

@Service
@Transactional
class AgentRunRecorder(private val runs: AgentRunRepository, private val clock: Clock) {
    fun startRun(draft: AgentRunDraft, model: String): UUID = runs.insert(newRun(draft, model)).id

    fun recordSuccess(runId: UUID, model: String, latencyMs: Int, tokensIn: Int, tokensOut: Int) {
        loadRun(runId).succeed(model, latencyMs, tokensIn, tokensOut, clock.nowMicros())
    }

    fun recordFailure(runId: UUID, status: AgentRunStatus, errorCode: String, latencyMs: Int) {
        loadRun(runId).fail(status, errorCode, latencyMs, clock.nowMicros())
    }

    fun recordGuardrailReply(draft: AgentRunDraft): UUID {
        val run = newRun(draft, SafetyReplies.GUARDRAIL_MODEL)
        run.succeed(SafetyReplies.GUARDRAIL_MODEL, latencyMs = 0, tokensIn = 0, tokensOut = 0, now = run.createdAt)
        return runs.insert(run).id
    }

    private fun newRun(draft: AgentRunDraft, model: String) = AgentRun(
        id = Ids.next(),
        kind = draft.kind,
        conversationId = draft.conversationId,
        personaId = draft.personaId,
        personaProfileId = draft.personaProfileId,
        model = model,
        promptHash = draft.prompt.sha256,
        promptPreview = draft.prompt.preview,
        historySize = draft.prompt.turns.size,
        createdAt = clock.nowMicros(),
    )

    private fun loadRun(runId: UUID): AgentRun = runs.findById(runId) ?: error("Прогон агента $runId не найден")
}
