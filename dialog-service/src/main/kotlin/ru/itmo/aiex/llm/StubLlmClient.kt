package ru.itmo.aiex.llm

import kotlin.math.absoluteValue

class StubLlmClient : LlmClient {
    override val model: String = MODEL

    override fun complete(request: LlmRequest): LlmResponse {
        val lastUserMessage = request.messages.lastOrNull { it.role == LlmRole.USER }?.content.orEmpty()
        when {
            TIMEOUT_MARKER in lastUserMessage -> throw LlmException(LlmException.Reason.TIMEOUT, "stub: имитация таймаута")
            DOWN_MARKER in lastUserMessage -> throw LlmException(LlmException.Reason.UNAVAILABLE, "stub: имитация недоступности")
        }
        val phrases = PHRASE_LINE.findAll(request.systemPrompt).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
        val text =
            if (request.operation.endsWith(".summary")) summary(phrases) else reply(phrases, lastUserMessage, request.messages.size)
        val limited = text.take(request.maxOutputTokens * CHARS_PER_TOKEN)
        val inputChars = request.systemPrompt.length + request.messages.sumOf { it.content.length }
        return LlmResponse(text = limited, model = MODEL, tokensIn = tokens(inputChars), tokensOut = tokens(limited.length))
    }

    private fun reply(phrases: List<String>, lastUserMessage: String, historySize: Int): String {
        if (phrases.isEmpty()) return FALLBACK_REPLY
        val index = (lastUserMessage.hashCode() + historySize).absoluteValue % phrases.size
        val first = phrases[index]
        return if (lastUserMessage.trimEnd().endsWith("?") && phrases.size > 1) {
            "$first ${phrases[(index + 1) % phrases.size]}"
        } else {
            first
        }
    }

    private fun summary(phrases: List<String>): String = if (phrases.isEmpty()) {
        "Пишет сдержанно, характерных фраз в корпусе мало."
    } else {
        "Узнаётся по фразам " + phrases.take(SUMMARY_PHRASES).joinToString(", ") { "«$it»" } + "."
    }

    private fun tokens(chars: Int) = (chars + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN

    companion object {
        const val MODEL = "stub-deterministic"
        const val TIMEOUT_MARKER = "[[llm:timeout]]"
        const val DOWN_MARKER = "[[llm:down]]"
        private const val FALLBACK_REPLY = "ммм. не знаю, что ответить"
        private const val CHARS_PER_TOKEN = 4
        private const val SUMMARY_PHRASES = 3
        private val PHRASE_LINE = Regex("""(?m)^\s*[-•]\s*«(.+)»\s*$""")
    }
}
