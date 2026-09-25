package ru.itmo.aiex.agent

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import ru.itmo.aiex.agent.dto.GenerateReplyCommand
import ru.itmo.aiex.agent.dto.HistoryMessage
import ru.itmo.aiex.agent.service.ReplyGenerator
import ru.itmo.aiex.agent.dto.Speaker
import ru.itmo.aiex.common.error.LlmUnavailableException
import ru.itmo.aiex.common.metrics.MetricsContributor
import ru.itmo.aiex.llm.StubLlmClient
import ru.itmo.aiex.persona.service.PersonaProfileQuery
import ru.itmo.aiex.persona.dto.PersonaProfileView
import ru.itmo.aiex.persona.dto.StyleView
import ru.itmo.aiex.testing.AbstractIntegrationTest
import java.time.Instant
import java.util.UUID

class AgentRunIT : AbstractIntegrationTest() {
    @MockkBean
    private lateinit var profiles: PersonaProfileQuery

    @Autowired
    private lateinit var replyGenerator: ReplyGenerator

    @Autowired
    private lateinit var metrics: List<MetricsContributor>

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val personaId = UUID.randomUUID()
    private val profileId = UUID.randomUUID()
    private val conversationId = UUID.randomUUID()
    private val phrases = listOf("ну привет", "опять ты", "я сплю вообще-то")

    @BeforeEach
    fun stubProfile() {
        every { profiles.findActiveProfile(personaId) } returns
            PersonaProfileView(
                personaId = personaId,
                profileId = profileId,
                versionNo = 3,
                personaName = "Маша",
                systemPrompt = "Ты - Маша, отвечаешь коротко.\n### Примеры реплик\n" + phrases.joinToString("\n") { "- «$it»" },
                style = StyleView(12.0, 0.0, emptyList(), 0.0, 0.9, "slow", phrases),
                traits = emptyList(),
                tags = listOf("холодная"),
            )
    }

    private fun command(count: Int, last: String): GenerateReplyCommand {
        val start = Instant.parse("2026-09-22T00:00:00Z")
        val earlier =
            (1 until count).map { i ->
                val speaker = if (i % 2 == 0) Speaker.PERSONA else Speaker.USER
                HistoryMessage(speaker, "сообщение $i", start.plusSeconds(i.toLong()))
            }
        return GenerateReplyCommand(conversationId, personaId, earlier + HistoryMessage(Speaker.USER, last, start.plusSeconds(count.toLong())))
    }

    private fun run(id: UUID): Map<String, Any?> = jdbcTemplate.queryForMap("SELECT * FROM agent.agent_runs WHERE id = ?", id)

    @Test
    fun `успешный прогон сохраняется с токенами, задержкой и ответом из фраз персоны`() {
        val reply = replyGenerator.generate(command(3, "привет, спишь"))

        assertThat(reply.text).isIn(phrases)
        assertThat(reply.model).isEqualTo(StubLlmClient.MODEL)
        val row = run(reply.agentRunId)
        assertThat(row["status"]).isEqualTo("SUCCESS")
        assertThat(row["model"]).isEqualTo(StubLlmClient.MODEL)
        assertThat(row["conversation_id"]).isEqualTo(conversationId)
        assertThat(row["persona_id"]).isEqualTo(personaId)
        assertThat(row["persona_profile_id"]).isEqualTo(profileId)
        assertThat(row["tokens_in"] as Int).isPositive()
        assertThat(row["tokens_out"] as Int).isPositive()
        assertThat(row["latency_ms"] as Int).isGreaterThanOrEqualTo(0)
        assertThat(row["history_size"]).isEqualTo(3)
        assertThat(row["prompt_hash"] as String).matches("[0-9a-f]{64}")
        assertThat(row["prompt_preview"] as String).startsWith("[system]\nТы - Маша").hasSizeLessThanOrEqualTo(200)
        assertThat(row["finished_at"]).isNotNull()
        assertThat(row["error_code"]).isNull()
    }

    @Test
    fun `провайдер недоступен - прогон FAILED и LlmUnavailableException`() {
        assertThatThrownBy { replyGenerator.generate(command(1, "эй ${StubLlmClient.DOWN_MARKER}")) }
            .isInstanceOf(LlmUnavailableException::class.java)
        val row = jdbcTemplate.queryForMap("SELECT status, error_code, finished_at FROM agent.agent_runs")
        assertThat(row["status"]).isEqualTo("FAILED")
        assertThat(row["error_code"]).isEqualTo("LLM_UNAVAILABLE")
        assertThat(row["finished_at"]).isNotNull()
    }

    @Test
    fun `таймаут провайдера - прогон TIMEOUT`() {
        assertThatThrownBy { replyGenerator.generate(command(1, "эй ${StubLlmClient.TIMEOUT_MARKER}")) }
            .isInstanceOf(LlmUnavailableException::class.java)
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM agent.agent_runs", String::class.java)).isEqualTo("TIMEOUT")
    }

    @Test
    fun `в промпт уходит не больше окна истории`() {
        val reply = replyGenerator.generate(command(35, "ты тут?"))
        assertThat(replyGenerator.historyWindow).isEqualTo(20)
        assertThat(run(reply.agentRunId)["history_size"] as Int).isEqualTo(20)
    }

    @Test
    fun `самоповреждение во входящем - ответ поддержки без вызова LLM`() {
        val reply = replyGenerator.generate(command(2, "я не хочу жить"))
        assertThat(reply.guardrailHits).hasSize(1)
        val row = run(reply.agentRunId)
        assertThat(row["status"]).isEqualTo("SUCCESS")
        assertThat(row["model"]).isEqualTo("guardrail")
    }

    @Test
    fun `вызов LLM внутри чужой транзакции запрещён`() {
        assertThatThrownBy {
            TransactionTemplate(transactionManager).executeWithoutResult { replyGenerator.generate(command(1, "привет")) }
        }.isInstanceOf(IllegalTransactionStateException::class.java)
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM agent.agent_runs", Long::class.javaObjectType)).isZero()
    }

    @Test
    fun `метрики агента отражают прогоны`() {
        replyGenerator.generate(command(1, "привет"))
        runCatching { replyGenerator.generate(command(1, StubLlmClient.TIMEOUT_MARKER)) }

        val agentMetrics = metrics.map { it.metrics() }.first { it.containsKey("agent.runs.total") }
        assertThat(agentMetrics)
            .containsEntry("agent.runs.total", 2L)
            .containsEntry("agent.runs.success", 1L)
            .containsEntry("agent.runs.timeout", 1L)
        assertThat(agentMetrics["agent.tokens.in"]).isPositive()
        assertThat(agentMetrics["agent.latency.avg_ms"]).isGreaterThanOrEqualTo(0L)
    }
}
