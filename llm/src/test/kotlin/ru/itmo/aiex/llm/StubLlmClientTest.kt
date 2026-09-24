package ru.itmo.aiex.llm

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class StubLlmClientTest {
    private val stub = StubLlmClient()

    private val prompt =
        """
        Ты - Маша.
        ### Примеры реплик
        - «ну привет»
        - «опять ты»
        - «я сплю вообще-то»
        """.trimIndent()

    private fun request(vararg userMessages: String, operation: String = "agent.reply", systemPrompt: String = prompt, maxOutputTokens: Int = 256) =
        LlmRequest(operation, systemPrompt, userMessages.map { LlmMessage(LlmRole.USER, it) }, maxOutputTokens)

    @Test
    fun `ответ детерминирован и собран из характерных фраз промпта`() {
        val first = stub.complete(request("привет, спишь"))
        val second = stub.complete(request("привет, спишь"))
        assertThat(first).isEqualTo(second)
        assertThat(first.text).isIn("ну привет", "опять ты", "я сплю вообще-то")
        assertThat(first.model).isEqualTo(StubLlmClient.MODEL)
        assertThat(first.tokensIn).isPositive()
        assertThat(first.tokensOut).isPositive()
    }

    @Test
    fun `на вопрос отвечает двумя фразами подряд`() {
        val reply = stub.complete(request("ты где?")).text
        assertThat(reply.split(" ").size).isGreaterThan(1)
        assertThat(listOf("ну привет", "опять ты", "я сплю вообще-то").count { reply.contains(it) }).isEqualTo(2)
    }

    @Test
    fun `без фраз в промпте - нейтральный ответ, для summary - описание`() {
        assertThat(stub.complete(request("привет", systemPrompt = "пусто")).text).isNotBlank()
        assertThat(stub.complete(request("опиши", operation = "persona.summary")).text).contains("«ну привет»")
        assertThat(stub.complete(request("опиши", operation = "persona.summary", systemPrompt = "пусто")).text).contains("мало")
    }

    @Test
    fun `бюджет ответа ограничивает длину`() {
        val reply = stub.complete(request("ты где?", maxOutputTokens = 1)).text
        assertThat(reply.length).isLessThanOrEqualTo(4)
    }

    @Test
    fun `маркеры имитируют таймаут и недоступность провайдера`() {
        assertThatThrownBy { stub.complete(request("эй ${StubLlmClient.TIMEOUT_MARKER}")) }
            .isInstanceOf(LlmException::class.java)
            .extracting("reason")
            .isEqualTo(LlmException.Reason.TIMEOUT)
        assertThatThrownBy { stub.complete(request(StubLlmClient.DOWN_MARKER)) }
            .extracting("reason")
            .isEqualTo(LlmException.Reason.UNAVAILABLE)
    }

    @Test
    fun `текст промпта и ключ не попадают в toString`() {
        assertThat(request("секретное сообщение").toString()).doesNotContain("секретное", "Маша")
        assertThat(LlmMessage(LlmRole.USER, "секрет").toString()).doesNotContain("секрет")
        assertThat(LlmResponse("секрет", "m", 1, 1).toString()).doesNotContain("секрет")
        assertThat(LlmProperties(apiKey = "sk-123").toString()).doesNotContain("sk-123").contains("***")
        assertThat(LlmProperties().toString()).contains("<none>")
    }
}
