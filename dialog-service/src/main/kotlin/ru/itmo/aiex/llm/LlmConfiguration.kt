package ru.itmo.aiex.llm

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LlmProperties::class)
class LlmConfiguration {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun llmClient(properties: LlmProperties, restClientBuilder: RestClient.Builder): LlmClient {
        log.info("LLM-провайдер: {}", properties)
        return when (properties.provider) {
            LlmProvider.STUB -> StubLlmClient()

            LlmProvider.OPENAI ->
                openAiCompatible(
                    properties,
                    restClientBuilder,
                    properties.baseUrl.orNullIfBlank() ?: OPENAI_BASE_URL,
                    requireNotNull(properties.model.orNullIfBlank()) { "Для AIEX_LLM_PROVIDER=openai нужен AIEX_LLM_MODEL" },
                )

            LlmProvider.OLLAMA ->
                OllamaLlmClient(
                    openAiCompatible(
                        properties,
                        restClientBuilder,
                        properties.baseUrl.orNullIfBlank() ?: OLLAMA_BASE_URL,
                        requireNotNull(properties.model.orNullIfBlank()) { "Для AIEX_LLM_PROVIDER=ollama нужен AIEX_LLM_MODEL" },
                    ),
                )
        }
    }

    private fun openAiCompatible(properties: LlmProperties, builder: RestClient.Builder, baseUrl: String, model: String): OpenAiCompatibleLlmClient {
        val timeout = Duration.ofMillis(properties.timeoutMs)
        val requestFactory =
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build()).apply { setReadTimeout(timeout) }
        val restClient =
            builder
                .clone()
                .baseUrl(baseUrl.trimEnd('/'))
                .requestFactory(requestFactory)
                .defaultHeaders { headers ->
                    properties.apiKey?.takeIf { it.isNotBlank() }?.let { headers.set(HttpHeaders.AUTHORIZATION, "Bearer $it") }
                }.build()
        return OpenAiCompatibleLlmClient(restClient, model, properties.maxRetries, properties.retryBackoffMs)
    }

    private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

    private companion object {
        const val OPENAI_BASE_URL = "https://api.openai.com/v1"
        const val OLLAMA_BASE_URL = "http://localhost:11434/v1"
    }
}
