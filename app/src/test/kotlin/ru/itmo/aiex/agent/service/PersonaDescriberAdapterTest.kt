package ru.itmo.aiex.agent.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.itmo.aiex.agent.dto.DescribePersonaCommand
import ru.itmo.aiex.agent.dto.PersonaTraitLine
import ru.itmo.aiex.agent.entity.AgentRunKind
import ru.itmo.aiex.agent.entity.AgentRunStatus
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.LlmUnavailableException
import ru.itmo.aiex.llm.LlmClient
import ru.itmo.aiex.llm.LlmException
import ru.itmo.aiex.llm.LlmRequest
import ru.itmo.aiex.llm.LlmResponse
import ru.itmo.aiex.llm.LlmRole
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class PersonaDescriberAdapterTest {
    private val now = Instant.parse("2026-09-22T03:00:00Z")
    private val runs = InMemoryAgentRunRepository()
    private val llm = mockk<LlmClient>()
    private val describer = PersonaDescriberAdapter(llm, AgentRunRecorder(runs, Clock.fixed(now, ZoneOffset.UTC)), Clock.fixed(now, ZoneOffset.UTC))

    private val personaId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        every { llm.model } returns "test-model"
    }

    private fun command() = DescribePersonaCommand(
        personaId = personaId,
        personaName = "Маша",
        traits = listOf(PersonaTraitLine("ревность", "high", 0.9)),
        samplePhrases = listOf("неа", "я сплю вообще-то"),
    )

    @Test
    fun `успех - прогон PERSONA_SUMMARY без беседы, резюме нормализовано`() {
        val request = slot<LlmRequest>()
        every { llm.complete(capture(request)) } returns LlmResponse("  Сдержанная.\n Ревнивая.  ", "provider-model", 120, 30)

        val description = describer.describe(command())

        assertThat(description.text).isEqualTo("Сдержанная. Ревнивая.")
        assertThat(description.model).isEqualTo("provider-model")

        val run = runs.all().single()
        assertThat(run.id).isEqualTo(description.agentRunId)
        assertThat(run.kind).isEqualTo(AgentRunKind.PERSONA_SUMMARY)
        assertThat(run.conversationId).isNull()
        assertThat(run.personaId).isEqualTo(personaId)
        assertThat(run.status).isEqualTo(AgentRunStatus.SUCCESS)
        assertThat(run.tokensIn).isEqualTo(120)
        assertThat(run.tokensOut).isEqualTo(30)
    }

    @Test
    fun `запрос помечен операцией persona_summary и содержит черты с фразами`() {
        val request = slot<LlmRequest>()
        every { llm.complete(capture(request)) } returns LlmResponse("Резюме.", "provider-model", 1, 1)

        describer.describe(command())

        assertThat(request.captured.operation).isEqualTo("persona.summary")
        assertThat(request.captured.systemPrompt).contains("Маша", "- ревность: high (0.90)", "- «неа»")
        assertThat(request.captured.maxOutputTokens).isEqualTo(PersonaSummaryPrompt.MAX_OUTPUT_TOKENS)
        assertThat(request.captured.temperature).isEqualTo(PersonaSummaryPrompt.TEMPERATURE)
        assertThat(request.captured.messages.map { it.role }).containsExactly(LlmRole.USER)
    }

    @Test
    fun `таймаут провайдера - прогон TIMEOUT и 503 LLM_UNAVAILABLE`() {
        every { llm.complete(any()) } throws LlmException(LlmException.Reason.TIMEOUT, "timeout")

        assertThatThrownBy { describer.describe(command()) }
            .isInstanceOf(LlmUnavailableException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.LLM_UNAVAILABLE)
            .hasCauseInstanceOf(LlmException::class.java)

        val run = runs.all().single()
        assertThat(run.kind).isEqualTo(AgentRunKind.PERSONA_SUMMARY)
        assertThat(run.status).isEqualTo(AgentRunStatus.TIMEOUT)
        assertThat(run.errorCode).isEqualTo("LLM_TIMEOUT")
    }

    @Test
    fun `недоступность провайдера - прогон FAILED`() {
        every { llm.complete(any()) } throws LlmException(LlmException.Reason.UNAVAILABLE, "провайдер лежит")

        assertThatThrownBy { describer.describe(command()) }.isInstanceOf(LlmUnavailableException::class.java)

        assertThat(runs.all().single().status).isEqualTo(AgentRunStatus.FAILED)
        assertThat(runs.all().single().errorCode).isEqualTo("LLM_UNAVAILABLE")
    }

    @Test
    fun `слишком длинное резюме обрезается`() {
        every { llm.complete(any()) } returns LlmResponse("а".repeat(PersonaSummaryPrompt.MAX_CHARS * 2), "provider-model", 1, 1)

        assertThat(describer.describe(command()).text).hasSize(PersonaSummaryPrompt.MAX_CHARS)
    }
}
