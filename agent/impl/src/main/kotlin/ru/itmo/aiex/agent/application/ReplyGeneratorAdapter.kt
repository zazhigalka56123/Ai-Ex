package ru.itmo.aiex.agent.application

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.agent.api.GenerateReplyCommand
import ru.itmo.aiex.agent.api.GeneratedReply
import ru.itmo.aiex.agent.api.GuardrailHit
import ru.itmo.aiex.agent.api.GuardrailTarget
import ru.itmo.aiex.agent.api.ReplyGenerator
import ru.itmo.aiex.agent.api.Speaker
import ru.itmo.aiex.agent.domain.AgentRunKind
import ru.itmo.aiex.agent.domain.AgentRunStatus
import ru.itmo.aiex.agent.domain.GuardrailFinding
import ru.itmo.aiex.agent.domain.Guardrails
import ru.itmo.aiex.agent.domain.PromptAssembler
import ru.itmo.aiex.agent.domain.SafetyReplies
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.LlmUnavailableException
import ru.itmo.aiex.common.moderation.FlagReason
import ru.itmo.aiex.llm.LlmClient
import ru.itmo.aiex.llm.LlmException
import ru.itmo.aiex.llm.LlmMessage
import ru.itmo.aiex.llm.LlmRequest
import ru.itmo.aiex.llm.LlmResponse
import ru.itmo.aiex.llm.LlmRole
import ru.itmo.aiex.persona.api.PersonaProfileQuery
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
class ReplyGeneratorAdapter(
    @param:Lazy private val profiles: PersonaProfileQuery,
    private val llm: LlmClient,
    private val recorder: AgentRunRecorder,
    private val properties: AgentProperties,
) : ReplyGenerator {
    private val log = LoggerFactory.getLogger(javaClass)

    override val historyWindow: Int get() = properties.historyWindow

    @Transactional(propagation = Propagation.NEVER)
    override fun generate(command: GenerateReplyCommand): GeneratedReply {
        val userMessage = command.history.lastOrNull()
        require(userMessage?.speaker == Speaker.USER) { "Последним в истории должно быть сообщение пользователя" }
        val profile =
            profiles.findActiveProfile(command.personaId)
                ?: throw ConflictException(ErrorCode.PERSONA_NOT_READY, "У персоны ${command.personaId} нет активного профиля - отвечать некому")
        val prompt = PromptAssembler.assemble(profile.systemPrompt, command.history, properties.historyWindow, properties.maxInputTokens)
        val draft = AgentRunDraft(AgentRunKind.REPLY, command.conversationId, command.personaId, profile.profileId, prompt)

        val inputFinding = Guardrails.inspect(checkNotNull(userMessage).text)
        return if (inputFinding?.reason == FlagReason.SELF_HARM) {
            supportInsteadOfReply(draft, inputFinding)
        } else {
            replyWithLlm(draft, listOfNotNull(inputFinding?.toHit(GuardrailTarget.USER_MESSAGE)))
        }
    }

    private fun supportInsteadOfReply(draft: AgentRunDraft, finding: GuardrailFinding): GeneratedReply {
        val runId = recorder.recordGuardrailReply(draft)
        logRun(runId, SafetyReplies.GUARDRAIL_MODEL, AgentRunStatus.SUCCESS, latencyMs = 0, tokensIn = 0, tokensOut = 0)
        return GeneratedReply(
            agentRunId = runId,
            text = SafetyReplies.SELF_HARM_SUPPORT,
            model = SafetyReplies.GUARDRAIL_MODEL,
            latencyMs = 0,
            tokensIn = 0,
            tokensOut = 0,
            guardrailHits = listOf(finding.toHit(GuardrailTarget.USER_MESSAGE)),
        )
    }

    private fun replyWithLlm(draft: AgentRunDraft, inputHits: List<GuardrailHit>): GeneratedReply {
        val runId = recorder.start(draft, llm.model)
        val request =
            LlmRequest(
                operation = OPERATION,
                systemPrompt = draft.prompt.system,
                messages = draft.prompt.turns.map { LlmMessage(if (it.speaker == Speaker.USER) LlmRole.USER else LlmRole.ASSISTANT, it.text) },
                maxOutputTokens = properties.maxOutputTokens,
            )
        val startedAt = System.nanoTime()
        val response = runCatching { llm.complete(request) }.getOrElse { failure -> throw recordFailure(runId, failure, elapsedMs(startedAt)) }
        val latencyMs = elapsedMs(startedAt)

        val outputFinding = Guardrails.inspect(response.text)
        recorder.succeed(runId, response.model, latencyMs, response.tokensIn, response.tokensOut)
        logRun(runId, response.model, AgentRunStatus.SUCCESS, latencyMs, response.tokensIn, response.tokensOut)
        return GeneratedReply(
            agentRunId = runId,
            text = replyText(response, outputFinding),
            model = response.model,
            latencyMs = latencyMs,
            tokensIn = response.tokensIn,
            tokensOut = response.tokensOut,
            guardrailHits = inputHits + listOfNotNull(outputFinding?.toHit(GuardrailTarget.PERSONA_REPLY)),
        )
    }

    private fun replyText(response: LlmResponse, outputFinding: GuardrailFinding?): String = when {
        outputFinding != null -> SafetyReplies.NEUTRAL_FALLBACK
        response.text.isBlank() -> SafetyReplies.EMPTY_FALLBACK
        else -> response.text.trim()
    }

    private fun recordFailure(runId: UUID, failure: Throwable, latencyMs: Int): Throwable {
        val status = if ((failure as? LlmException)?.reason == LlmException.Reason.TIMEOUT) AgentRunStatus.TIMEOUT else AgentRunStatus.FAILED
        val errorCode = (failure as? LlmException)?.let { "LLM_${it.reason.name}" } ?: ErrorCode.INTERNAL_ERROR.name
        recorder.fail(runId, status, errorCode, latencyMs)
        logRun(runId, llm.model, status, latencyMs, tokensIn = null, tokensOut = null, errorCode = errorCode)
        return if (failure is LlmException) {
            val detail = if (status == AgentRunStatus.TIMEOUT) "не ответил вовремя" else "недоступен"
            LlmUnavailableException("Персона сейчас не может ответить: LLM-провайдер $detail. Сообщение сохранено, повторите позже", failure)
        } else {
            failure
        }
    }

    private fun logRun(
        runId: UUID,
        model: String,
        status: AgentRunStatus,
        latencyMs: Int,
        tokensIn: Int?,
        tokensOut: Int?,
        errorCode: String? = null,
    ) {
        log.info(
            "agent_run id={} status={} model={} latencyMs={} tokensIn={} tokensOut={} errorCode={}",
            runId,
            status,
            model,
            latencyMs,
            tokensIn,
            tokensOut,
            errorCode,
        )
    }

    private fun elapsedMs(startedAt: Long): Int = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt).toInt()

    private fun GuardrailFinding.toHit(target: GuardrailTarget) = GuardrailHit(target, reason, "guardrail ${target.name}: $rule")

    private companion object {
        const val OPERATION = "agent.reply"
    }
}
