package ru.itmo.aiex.llm

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.SocketTimeoutException

class OpenAiCompatibleLlmClientTest {
    private val builder = RestClient.builder().baseUrl("http://llm.test/v1")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val sleeps = mutableListOf<Long>()
    private val client = OpenAiCompatibleLlmClient(builder.build(), "gpt-test", maxRetries = 2, retryBackoffMs = 100) { sleeps += it }

    private val request =
        LlmRequest("agent.reply", "Ты - Маша", listOf(LlmMessage(LlmRole.USER, "привет"), LlmMessage(LlmRole.ASSISTANT, "ну")), 64, 0.5)

    private val okBody =
        """
        {"id":"x","model":"gpt-test-2026","choices":[{"index":0,"message":{"role":"assistant","content":"  ну привет  "}}],
         "usage":{"prompt_tokens":12,"completion_tokens":3,"total_tokens":15}}
        """.trimIndent()

    @Test
    fun `запрос в формате chat completions и разбор ответа`() {
        server
            .expect(requestTo("http://llm.test/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(
                content().json(
                    """
                    {"model":"gpt-test","max_tokens":64,"temperature":0.5,
                     "messages":[{"role":"system","content":"Ты - Маша"},{"role":"user","content":"привет"},{"role":"assistant","content":"ну"}]}
                    """.trimIndent(),
                ),
            ).andRespond(withSuccess(okBody, MediaType.APPLICATION_JSON))

        val response = client.complete(request)

        assertThat(response).isEqualTo(LlmResponse("ну привет", "gpt-test-2026", 12, 3))
        server.verify()
    }

    @Test
    fun `5xx и 429 повторяются с экспоненциальной задержкой`() {
        server.expect(requestTo("http://llm.test/v1/chat/completions")).andRespond(withStatus(HttpStatus.BAD_GATEWAY))
        server.expect(requestTo("http://llm.test/v1/chat/completions")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))
        server.expect(requestTo("http://llm.test/v1/chat/completions")).andRespond(withSuccess(okBody, MediaType.APPLICATION_JSON))

        assertThat(client.complete(request).text).isEqualTo("ну привет")
        assertThat(sleeps).containsExactly(100L, 200L)
    }

    @Test
    fun `ретраи кончились - UNAVAILABLE`() {
        server.expect(ExpectedCount.times(3), requestTo("http://llm.test/v1/chat/completions")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))

        assertThatThrownBy { client.complete(request) }
            .isInstanceOf(LlmException::class.java)
            .extracting("reason")
            .isEqualTo(LlmException.Reason.UNAVAILABLE)
        server.verify()
    }

    @Test
    fun `4xx не повторяется - REJECTED`() {
        server.expect(ExpectedCount.once(), requestTo("http://llm.test/v1/chat/completions")).andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        assertThatThrownBy { client.complete(request) }.extracting("reason").isEqualTo(LlmException.Reason.REJECTED)
        assertThat(sleeps).isEmpty()
        server.verify()
    }

    @Test
    fun `таймаут сокета - TIMEOUT`() {
        server
            .expect(ExpectedCount.times(3), requestTo("http://llm.test/v1/chat/completions"))
            .andRespond(withException(SocketTimeoutException("read timed out")))

        assertThatThrownBy { client.complete(request) }.extracting("reason").isEqualTo(LlmException.Reason.TIMEOUT)
    }

    @Test
    fun `пустой список choices - UNAVAILABLE`() {
        server
            .expect(ExpectedCount.times(3), requestTo("http://llm.test/v1/chat/completions"))
            .andRespond(withSuccess("""{"choices":[]}""", MediaType.APPLICATION_JSON))

        assertThatThrownBy { client.complete(request) }.extracting("reason").isEqualTo(LlmException.Reason.UNAVAILABLE)
    }

    @Test
    fun `битый JSON - UNAVAILABLE`() {
        server
            .expect(ExpectedCount.times(3), requestTo("http://llm.test/v1/chat/completions"))
            .andRespond(withSuccess("не json", MediaType.APPLICATION_JSON))

        assertThatThrownBy { client.complete(request) }.extracting("reason").isEqualTo(LlmException.Reason.UNAVAILABLE)
    }
}
