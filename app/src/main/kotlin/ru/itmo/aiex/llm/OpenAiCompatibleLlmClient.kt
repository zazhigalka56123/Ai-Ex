package ru.itmo.aiex.llm

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.io.InterruptedIOException
import java.net.http.HttpTimeoutException

class OpenAiCompatibleLlmClient(
    private val restClient: RestClient,
    override val model: String,
    private val maxRetries: Int,
    private val retryBackoffMs: Long,
    private val sleeper: (Long) -> Unit = Thread::sleep,
) : LlmClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun complete(request: LlmRequest): LlmResponse {
        var attempt = 0
        while (true) {
            try {
                return call(request)
            } catch (ex: LlmException) {
                if (!ex.reason.retryable || attempt >= maxRetries) throw ex
                val delay = retryBackoffMs shl attempt
                log.warn("LLM {}: {} - повтор {}/{} через {} мс", request.operation, ex.reason, attempt + 1, maxRetries, delay)
                sleeper(delay)
                attempt++
            }
        }
    }

    private fun call(request: LlmRequest): LlmResponse {
        val body =
            ChatRequest(
                model = model,
                messages =
                listOf(ChatMessage("system", request.systemPrompt)) + request.messages.map { ChatMessage(it.role.name.lowercase(), it.content) },
                maxTokens = request.maxOutputTokens,
                temperature = request.temperature,
            )
        val response =
            try {
                restClient
                    .post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .onStatus(::isRetryableStatus) { _, res -> throw LlmException(LlmException.Reason.UNAVAILABLE, "HTTP ${res.statusCode.value()}") }
                    .onStatus(HttpStatusCode::is4xxClientError) { _, res ->
                        throw LlmException(LlmException.Reason.REJECTED, "HTTP ${res.statusCode.value()}")
                    }.body(ChatResponse::class.java)
            } catch (ex: ResourceAccessException) {
                val timeout = generateSequence<Throwable>(ex) { it.cause }.any { it is HttpTimeoutException || it is InterruptedIOException }
                val reason = if (timeout) LlmException.Reason.TIMEOUT else LlmException.Reason.UNAVAILABLE
                throw LlmException(reason, "Провайдер LLM недоступен: ${ex.message}", ex)
            } catch (ex: RestClientException) {
                throw LlmException(LlmException.Reason.UNAVAILABLE, "Некорректный ответ провайдера LLM: ${ex.message}", ex)
            }
        val text =
            response?.choices?.firstOrNull()?.message?.content?.trim()
                ?: throw LlmException(LlmException.Reason.UNAVAILABLE, "Провайдер вернул пустой ответ")
        return LlmResponse(
            text = text,
            model = response.model ?: model,
            tokensIn = response.usage?.promptTokens ?: 0,
            tokensOut = response.usage?.completionTokens ?: 0,
        )
    }

    private fun isRetryableStatus(status: HttpStatusCode): Boolean = status.is5xxServerError || status.value() == TOO_MANY_REQUESTS

    internal data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        @JsonProperty("max_tokens") val maxTokens: Int,
        val temperature: Double,
    )

    internal data class ChatMessage(val role: String, val content: String?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class ChatResponse(val model: String? = null, val choices: List<Choice> = emptyList(), val usage: Usage? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class Choice(val message: ChatMessage? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class Usage(
        @JsonProperty("prompt_tokens") val promptTokens: Int? = null,
        @JsonProperty("completion_tokens") val completionTokens: Int? = null,
    )

    private companion object {
        const val TOO_MANY_REQUESTS = 429
    }
}
