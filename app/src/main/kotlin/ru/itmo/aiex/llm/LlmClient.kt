package ru.itmo.aiex.llm

interface LlmClient {
    val model: String

    fun complete(request: LlmRequest): LlmResponse
}
