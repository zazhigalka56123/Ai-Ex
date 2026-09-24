package ru.itmo.aiex.llm

data class LlmRequest(
    val operation: String,
    val systemPrompt: String,
    val messages: List<LlmMessage>,
    val maxOutputTokens: Int,
    val temperature: Double = DEFAULT_TEMPERATURE,
) {
    init {
        require(maxOutputTokens > 0) { "maxOutputTokens должен быть > 0" }
    }

    override fun toString(): String =
        "LlmRequest(operation=$operation, systemChars=${systemPrompt.length}, messages=${messages.size}, maxOutputTokens=$maxOutputTokens)"

    companion object {
        const val DEFAULT_TEMPERATURE = 0.8
    }
}
