package ru.itmo.aiex.llm

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("aiex.llm")
data class LlmProperties(
    val provider: LlmProvider = LlmProvider.STUB,
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val model: String? = null,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val retryBackoffMs: Long = DEFAULT_BACKOFF_MS,
) {
    init {
        require(timeoutMs > 0) { "aiex.llm.timeout-ms должен быть > 0" }
        require(maxRetries >= 0) { "aiex.llm.max-retries должен быть ≥ 0" }
    }

    override fun toString(): String =
        "LlmProperties(provider=$provider, baseUrl=$baseUrl, apiKey=${if (apiKey.isNullOrBlank()) "<none>" else "***"}, " +
            "model=$model, timeoutMs=$timeoutMs, maxRetries=$maxRetries)"

    companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L
        const val DEFAULT_MAX_RETRIES = 2
        const val DEFAULT_BACKOFF_MS = 200L
    }
}
