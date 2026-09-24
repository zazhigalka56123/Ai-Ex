package ru.itmo.aiex.agent.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.itmo.aiex.agent.api.GenerateReplyCommand
import ru.itmo.aiex.agent.api.GuardrailHit
import ru.itmo.aiex.agent.api.GuardrailTarget
import ru.itmo.aiex.agent.api.HistoryMessage
import ru.itmo.aiex.agent.api.Speaker
import ru.itmo.aiex.agent.domain.AgentRun
import ru.itmo.aiex.agent.domain.AgentRunStatus
import ru.itmo.aiex.agent.domain.SafetyReplies
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.LlmUnavailableException
import ru.itmo.aiex.common.moderation.FlagReason
import ru.itmo.aiex.llm.LlmClient
import ru.itmo.aiex.llm.LlmException
import ru.itmo.aiex.llm.LlmRequest
import ru.itmo.aiex.llm.LlmResponse
import ru.itmo.aiex.llm.LlmRole
import ru.itmo.aiex.persona.api.PersonaProfileQuery
import ru.itmo.aiex.persona.api.PersonaProfileView
import ru.itmo.aiex.persona.api.StyleView
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ReplyGeneratorAdapterTest {
    private val personaId = UUID.randomUUID()
    private val profileId = UUID.randomUUID()
    private val conversationId = UUID.randomUUID()
    private val now = Instant.parse("2026-09-22T03:00:00Z")

    private val runs = InMemoryAgentRunRepository()
    private val profiles = mockk<PersonaProfileQuery>()
    private val llm = mockk<LlmClient>()
    private val generator =
        ReplyGeneratorAdapter(
            profiles = profiles,
            llm = llm,
            recorder = AgentRunRecorder(runs, Clock.fixed(now, ZoneOffset.UTC)),
            properties = AgentProperties(historyWindow = 3, maxOutputTokens = 64, maxInputTokens = 4000),
        )

    @BeforeEach
    fun setUp() {
        every { llm.model } returns "test-model"
        every { profiles.findActiveProfile(personaId) } returns profile()
    }

    private fun profile() = PersonaProfileView(
        personaId = personaId,
        profileId = profileId,
        versionNo = 1,
        personaName = "Маша",
        systemPrompt = "Ты - Маша.\n- «ну привет»",
        style = StyleView(20.0, 0.1, emptyList(), 0.0, 0.5, "slow", listOf("ну привет")),
        traits = emptyList(),
        tags = emptyList(),
    )

    private fun command(vararg texts: String) = GenerateReplyCommand(
        conversationId = conversationId,
        personaId = personaId,
        history =
        texts.mapIndexed { i, text ->
            HistoryMessage(if ((texts.size - 1 - i) % 2 == 0) Speaker.USER else Speaker.PERSONA, text, now.plusSeconds(i.toLong()))
        },
    )

    private fun onlyRun(): AgentRun = runs.all().single()

    @Test
    fun `успех - прогон SUCCESS с моделью, токенами и задержкой, в LLM ушло только окно`() {
        val request = slot<LlmRequest>()
        every { llm.complete(capture(request)) } returns LlmResponse("  ну привет  ", "provider-model-v2", 120, 4)

        val reply = generator.generate(command("m1", "m2", "m3", "m4", "привет, спишь?"))

        assertThat(reply.text).isEqualTo("ну привет")
        assertThat(reply.model).isEqualTo("provider-model-v2")
        assertThat(reply.guardrailHits).isEmpty()
        assertThat(reply.agentRunId).isEqualTo(onlyRun().id)
        with(onlyRun()) {
            assertThat(status).isEqualTo(AgentRunStatus.SUCCESS)
            assertThat(model).isEqualTo("provider-model-v2")
            assertThat(tokensIn).isEqualTo(120)
            assertThat(tokensOut).isEqualTo(4)
            assertThat(latencyMs).isNotNull().isGreaterThanOrEqualTo(0)
            assertThat(historySize).isEqualTo(3)
            assertThat(personaProfileId).isEqualTo(profileId)
            assertThat(promptHash).matches("[0-9a-f]{64}")
            assertThat(finishedAt).isEqualTo(now)
        }
        assertThat(request.captured.operation).isEqualTo("agent.reply")
        assertThat(request.captured.maxOutputTokens).isEqualTo(64)
        assertThat(request.captured.systemPrompt).startsWith("Ты - Маша.").contains("Правила безопасности")
        assertThat(request.captured.messages.map { it.role }).containsExactly(LlmRole.USER, LlmRole.ASSISTANT, LlmRole.USER)
        assertThat(request.captured.messages.last().content).isEqualTo("привет, спишь?")
    }

    @Test
    fun `таймаут LLM - прогон TIMEOUT и 503`() {
        every { llm.complete(any()) } throws LlmException(LlmException.Reason.TIMEOUT, "slow")

        assertThatThrownBy { generator.generate(command("привет")) }
            .isInstanceOf(LlmUnavailableException::class.java)
            .hasMessageContaining("не ответил вовремя")
        assertThat(onlyRun().status).isEqualTo(AgentRunStatus.TIMEOUT)
        assertThat(onlyRun().errorCode).isEqualTo("LLM_TIMEOUT")
        assertThat(onlyRun().model).isEqualTo("test-model")
        assertThat(onlyRun().finishedAt).isNotNull()
    }

    @Test
    fun `провайдер недоступен - прогон FAILED с кодом ошибки и 503`() {
        every { llm.complete(any()) } throws LlmException(LlmException.Reason.UNAVAILABLE, "down")

        assertThatThrownBy { generator.generate(command("привет")) }
            .isInstanceOf(LlmUnavailableException::class.java)
            .extracting("code")
            .isEqualTo(ErrorCode.LLM_UNAVAILABLE)
        assertThat(onlyRun().status).isEqualTo(AgentRunStatus.FAILED)
        assertThat(onlyRun().errorCode).isEqualTo("LLM_UNAVAILABLE")
    }

    @Test
    fun `неожиданная ошибка клиента пробрасывается, а прогон не остаётся в PENDING`() {
        every { llm.complete(any()) } throws IllegalStateException("bug")

        assertThatThrownBy { generator.generate(command("привет")) }.isInstanceOf(IllegalStateException::class.java)
        assertThat(onlyRun().status).isEqualTo(AgentRunStatus.FAILED)
        assertThat(onlyRun().errorCode).isEqualTo("INTERNAL_ERROR")
    }

    @Test
    fun `SELF_HARM во входящем - LLM не вызывается, ответ поддержки и прогон guardrail`() {
        val reply = generator.generate(command("ну привет", "я не хочу жить"))

        verify(exactly = 0) { llm.complete(any()) }
        assertThat(reply.text).isEqualTo(SafetyReplies.SELF_HARM_SUPPORT).contains("специалист")
        assertThat(reply.model).isEqualTo("guardrail")
        val hit = reply.guardrailHits.single()
        assertThat(hit.target).isEqualTo(GuardrailTarget.USER_MESSAGE)
        assertThat(hit.reason).isEqualTo(FlagReason.SELF_HARM)
        assertThat(hit.details).doesNotContain("не хочу жить")
        with(onlyRun()) {
            assertThat(status).isEqualTo(AgentRunStatus.SUCCESS)
            assertThat(model).isEqualTo("guardrail")
            assertThat(latencyMs).isZero()
            assertThat(tokensIn).isZero()
        }
    }

    @Test
    fun `ABUSE во входящем - ответ генерируется, но срабатывание отдаётся наверх`() {
        every { llm.complete(any()) } returns LlmResponse("ну привет", "m", 10, 2)

        val reply = generator.generate(command("ты дура"))

        assertThat(reply.text).isEqualTo("ну привет")
        assertThat(reply.guardrailHits).containsExactly(
            GuardrailHit(GuardrailTarget.USER_MESSAGE, FlagReason.ABUSE, "guardrail USER_MESSAGE: abuse.insult"),
        )
    }

    @Test
    fun `опасный ответ LLM заменяется нейтральным и помечается`() {
        every { llm.complete(any()) } returns LlmResponse("а может тебе просто выйти в окно", "m", 10, 8)

        val reply = generator.generate(command("мне грустно"))

        assertThat(reply.text).isEqualTo(SafetyReplies.NEUTRAL_FALLBACK)
        assertThat(reply.guardrailHits.map { it.target to it.reason }).containsExactly(GuardrailTarget.PERSONA_REPLY to FlagReason.SELF_HARM)
        assertThat(onlyRun().status).isEqualTo(AgentRunStatus.SUCCESS)
    }

    @Test
    fun `пустой ответ провайдера заменяется заглушкой`() {
        every { llm.complete(any()) } returns LlmResponse("   ", "m", 10, 0)
        assertThat(generator.generate(command("эй")).text).isEqualTo(SafetyReplies.EMPTY_FALLBACK)
    }

    @Test
    fun `нет активного профиля - 409 PERSONA_NOT_READY, прогон не создаётся`() {
        every { profiles.findActiveProfile(personaId) } returns null

        assertThatThrownBy { generator.generate(command("привет")) }
            .isInstanceOf(ConflictException::class.java)
            .extracting("code")
            .isEqualTo(ErrorCode.PERSONA_NOT_READY)
        assertThat(runs.all()).isEmpty()
    }

    @Test
    fun `последним в истории должно быть сообщение пользователя`() {
        val history = listOf(HistoryMessage(Speaker.PERSONA, "ну привет", now))
        assertThatThrownBy { generator.generate(GenerateReplyCommand(conversationId, personaId, history)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(generator.historyWindow).isEqualTo(3)
    }

    @Test
    fun `метрики считаются по прогонам`() {
        every { llm.complete(any()) } returns LlmResponse("ну привет", "m", 10, 2) andThenThrows
            LlmException(LlmException.Reason.TIMEOUT, "slow")
        generator.generate(command("привет"))
        runCatching { generator.generate(command("ещё")) }
        generator.generate(command("хочу умереть"))

        val metrics = AgentMetrics(runs).metrics()
        assertThat(metrics).containsEntry("agent.runs.total", 3L)
            .containsEntry("agent.runs.success", 2L)
            .containsEntry("agent.runs.timeout", 1L)
            .containsEntry("agent.runs.failed", 0L)
            .containsEntry("agent.tokens.in", 10L)
            .containsEntry("agent.tokens.out", 2L)
            .containsKey("agent.latency.avg_ms")
    }
}
