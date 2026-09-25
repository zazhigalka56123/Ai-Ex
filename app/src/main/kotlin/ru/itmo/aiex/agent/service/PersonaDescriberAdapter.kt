package ru.itmo.aiex.agent.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.agent.dto.DescribePersonaCommand

import ru.itmo.aiex.agent.dto.PersonaDescription
import ru.itmo.aiex.agent.entity.AgentRunKind
import ru.itmo.aiex.agent.entity.AgentRunStatus

import ru.itmo.aiex.common.error.LlmUnavailableException
import ru.itmo.aiex.common.time.nowMicros
import ru.itmo.aiex.llm.LlmClient
import ru.itmo.aiex.llm.LlmException
import ru.itmo.aiex.llm.LlmMessage
import ru.itmo.aiex.llm.LlmRequest
import ru.itmo.aiex.llm.LlmRole
import java.time.Clock
import java.util.concurrent.TimeUnit

@Component
class PersonaDescriberAdapter(private val llm: LlmClient, private val recorder: AgentRunRecorder, private val clock: Clock) : PersonaDescriber {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(propagation = Propagation.NEVER)
    override fun describe(command: DescribePersonaCommand): PersonaDescription {
        val prompt = PersonaSummaryPrompt.assemble(command, clock.nowMicros())
        val draft = AgentRunDraft(AgentRunKind.PERSONA_SUMMARY, null, command.personaId, null, prompt)
        val runId = recorder.start(draft, llm.model)
        val request =
            LlmRequest(
                operation = OPERATION,
                systemPrompt = prompt.system,
                messages = prompt.turns.map { LlmMessage(LlmRole.USER, it.text) },
                maxOutputTokens = PersonaSummaryPrompt.MAX_OUTPUT_TOKENS,
                temperature = PersonaSummaryPrompt.TEMPERATURE,
            )
        val startedAt = System.nanoTime()
        val response =
            try {
                llm.complete(request)
            } catch (ex: LlmException) {
                val status = if (ex.reason == LlmException.Reason.TIMEOUT) AgentRunStatus.TIMEOUT else AgentRunStatus.FAILED
                recorder.fail(runId, status, "LLM_${ex.reason.name}", elapsedMs(startedAt))
                log.info("agent_run id={} kind={} status={} errorCode=LLM_{}", runId, AgentRunKind.PERSONA_SUMMARY, status, ex.reason)
                throw LlmUnavailableException("Не удалось описать характер персоны: LLM недоступен (${ex.reason})", ex)
            }
        val latencyMs = elapsedMs(startedAt)
        recorder.succeed(runId, response.model, latencyMs, response.tokensIn, response.tokensOut)
        log.info(
            "agent_run id={} kind={} status=SUCCESS model={} latencyMs={} tokensIn={} tokensOut={}",
            runId,
            AgentRunKind.PERSONA_SUMMARY,
            response.model,
            latencyMs,
            response.tokensIn,
            response.tokensOut,
        )
        return PersonaDescription(runId, PersonaSummaryPrompt.normalize(response.text), response.model)
    }

    private fun elapsedMs(startedAt: Long): Int = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt).toInt()

    private companion object {
        const val OPERATION = "persona.summary"
    }
}
