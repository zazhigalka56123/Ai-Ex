package ru.itmo.aiex.llm

class LlmException(val reason: Reason, message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    enum class Reason(val retryable: Boolean) {
        TIMEOUT(true),
        UNAVAILABLE(true),
        REJECTED(false),
    }
}
